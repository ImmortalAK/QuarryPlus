package com.yogpc.qp.machines.workbench

import java.nio.charset.StandardCharsets
import java.util.{Collections, Comparator}

import com.google.gson.{Gson, GsonBuilder, JsonObject}
import com.yogpc.qp.machines.base.APowerTile
import com.yogpc.qp.utils.ItemDamage
import com.yogpc.qp.{QuarryPlus, _}
import net.minecraft.inventory.IInventory
import net.minecraft.item.crafting.{IRecipeHidden, IRecipeSerializer}
import net.minecraft.item.{Item, ItemStack}
import net.minecraft.network.PacketBuffer
import net.minecraft.resources.{IResource, IResourceManager}
import net.minecraft.util.{JsonUtils, ResourceLocation}
import net.minecraft.world.World
import net.minecraftforge.common.crafting.{CraftingHelper, RecipeType}
import net.minecraftforge.fml.server.ServerLifecycleHooks
import org.apache.commons.io.IOUtils

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.Try

abstract sealed class WorkbenchRecipes(val location: ResourceLocation, val output: ItemDamage, val energy: Long, val showInJEI: Boolean = true)
  extends IRecipeHidden(location) with Ordered[WorkbenchRecipes] {
  val microEnergy = energy
  val size: Int

  def inputs: Seq[Seq[IngredientWithCount]]

  def inputsJ(): java.util.List[java.util.List[IngredientWithCount]] = inputs.map(_.asJava).asJava

  def hasContent: Boolean = true

  def getOutput: ItemStack = output.toStack()

  override val toString = s"WorkbenchRecipes(output=$output, energy=$energy)"

  override val hashCode: Int = output.hashCode() ^ energy.##

  override def equals(obj: scala.Any): Boolean = {
    super.equals(obj) || {
      obj match {
        case r: WorkbenchRecipes => location == r.location && output == r.output && energy == r.energy
        case _ => false
      }
    }
  }

  override def compare(that: WorkbenchRecipes) = {
    WorkbenchRecipes.recipeOrdering.compare(this, that)
  }

  override def matches(inv: IInventory, worldIn: World): Boolean = {
    val inputInv = Range(0, inv.getSizeInventory).map(inv.getStackInSlot)
    hasContent && inputs.forall(in => inputInv.exists(invStack => in.exists(_.matches(invStack))))
  }

  override def getCraftingResult(inv: IInventory) = getOutput

  override def canFit(width: Int, height: Int) = true

  override def getSerializer = WorkbenchRecipes.Serializer

  override def getType = WorkbenchRecipes.recipeType
}

private final class IngredientRecipe(location: ResourceLocation, o: ItemStack, e: Long, s: Boolean, seq: Seq[Seq[IngredientWithCount]])
  extends WorkbenchRecipes(location, ItemDamage(o), e, s) {
  override val size = seq.size

  override def inputs = seq

  override def getOutput = o.copy()
}

object WorkbenchRecipes {

  private[this] val recipes_internal = mutable.Map.empty[ResourceLocation, WorkbenchRecipes]

  val dummyRecipe: WorkbenchRecipes = new WorkbenchRecipes(
    new ResourceLocation(QuarryPlus.modID, "builtin_dummy"), ItemDamage.invalid, energy = 0, showInJEI = false) {
    override val inputs = Nil
    override val microEnergy = 0L
    override val inputsJ: java.util.List[java.util.List[IngredientWithCount]] = Collections.emptyList()
    override val size: Int = 0
    override val toString: String = "WorkbenchRecipe NoRecipe"
    override val hasContent: Boolean = false
  }

  val recipeOrdering: Comparator[WorkbenchRecipes] =
    Ordering.by((a: WorkbenchRecipes) => a.energy) thenComparing Ordering.by((a: WorkbenchRecipes) => Item.getIdFromItem(a.output.item))

  val recipeLocation = new ResourceLocation(QuarryPlus.modID, "workbench_recipe")
  val recipeType = RecipeType.get(recipeLocation, classOf[WorkbenchRecipes])
  private[this] final val conditionMessage = "Condition is false"

  def recipes: Map[ResourceLocation, WorkbenchRecipes] = {
    Option(ServerLifecycleHooks.getCurrentServer).map(_.getRecipeManager.getRecipes.asScala.collect {
      case recipes: WorkbenchRecipes => (recipes.location, recipes)
    }.toMap).getOrElse(Map.empty) ++ recipes_internal
  }

  def recipeSize: Int = recipes.size

  def removeRecipe(output: ItemDamage): Unit = recipes_internal.retain { case (_, r) => r.output != output }

  def removeRecipe(location: ResourceLocation): Unit = recipes_internal.remove(location)

  def getRecipe(inputs: java.util.List[ItemStack]): java.util.List[WorkbenchRecipes] = {
    val asScala = inputs.asScala
    recipes.filter {
      case (_, workRecipe) if workRecipe.hasContent =>
        workRecipe.inputs.forall(i => {
          asScala.exists(t => i.exists(_.matches(t)))
        })
      case _ => false
    }.values.toList.sorted.asJava
  }

  def addIngredientRecipe(location: ResourceLocation, output: ItemStack, energy: Double, inputs: java.util.List[java.util.List[IngredientWithCount]]): Unit = {
    val scalaInput = inputs.asScala.map(_.asScala.toSeq)
    val newRecipe = new IngredientRecipe(location, output, (energy * APowerTile.MicroJtoMJ).toLong, s = true, scalaInput)
    if (energy > 0) {
      recipes_internal put(location, newRecipe)
    } else {
      QuarryPlus.LOGGER.error(s"Energy of Workbench Recipe is 0. $newRecipe")
    }
  }

  def getRecipeMap: Map[ResourceLocation, WorkbenchRecipes] = recipes

  def getRecipeFromResult(stack: ItemStack): java.util.Optional[WorkbenchRecipes] = {
    if (stack.isEmpty) return java.util.Optional.empty()
    val id = ItemDamage(stack)
    recipes.find { case (_, r) => r.output == id }.map(_._2).asJava
  }

  def registerJsonRecipe(resourceManager: IResourceManager): Unit = {
    recipes_internal.clear() // Loading is called every time the player enters world.
    val gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping.create

    resourceManager.getAllResourceLocations("quarryplus/workbench", s => s.endsWith(".json") && !s.startsWith("_")).asScala
      .map(r => pathToJson(resourceManager.getAllResources(r).asScala.lastOption, gson, r))
      .map(load)
      .flatMap {
        case Left(value) => QuarryPlus.LOGGER.error("QuarryPlus recipe loading error.", value); None
        case Right(value) => Some(value)
      }.foreach(r => recipes_internal.put(r.location, r))
  }

  private def pathToJson(resourceOpt: Option[IResource], gson: Gson, location: ResourceLocation) = {
    for (resource <- resourceOpt.toRight(new RuntimeException(s"Resource: $location isn't found."));
         readString <- Try(IOUtils.toString(resource.getInputStream, StandardCharsets.UTF_8)).toEither;
         json <- Try(JsonUtils.fromJson(gson, readString, classOf[JsonObject])).toEither) yield {
      val matcher = namePattern.pattern.matcher(location.toString)
      if (matcher.matches())
        json.addProperty("path", matcher.group(1) + ":workbench/" + matcher.group(2))
      json
    }
  }

  def load(obj: Either[Throwable, JsonObject]): Either[String, WorkbenchRecipes] = {

    obj.left.map(_.toString)
      .filterOrElse(json => CraftingHelper.processConditions(json, "conditions"),
        conditionMessage)
      .filterOrElse(json => JsonUtils.getString(json, "type") == recipeLocation.toString,
        "Not a workbench recipe.")
      .flatMap { json =>
        val result = CraftingHelper.getItemStack(JsonUtils.getJsonObject(json, "result"), true)
        val id = JsonUtils.getString(json, "id", "")
        // divided to compute lazy.
        val location = if (id == "") QuarryPlus.modID + ":" + JsonUtils.getString(json, "path") else id
        if (!result.isEmpty) {
          (for (recipe <- Try(JsonUtils.getJsonArray(json, "ingredients").asScala.map(IngredientWithCount.getSeq).toSeq);
                energy <- Try(JsonUtils.getString(json, "energy", "1000").toDouble * APowerTile.MicroJtoMJ);
                showInJEI <- Try(JsonUtils.getBoolean(json, "showInJEI", true))) yield {
            new IngredientRecipe(new ResourceLocation(location), result, energy.toLong, showInJEI, recipe)
          }).toEither.left.map(_.toString)
        } else {
          Left("Result item is empty.")
        }
      }
      .filterOrElse(_.energy > 0, "Energy must be over than 0.")
  }

  private[this] final val namePattern = "(.+):quarryplus/workbench/(.+).json".r

  object Serializer extends IRecipeSerializer[WorkbenchRecipes] {
    override def read(recipeId: ResourceLocation, json: JsonObject): WorkbenchRecipes = {
      json.addProperty("id", recipeId.toString)
      load(Right(json)) match {
        case Right(value) => value
        case Left(value) if value == conditionMessage => WorkbenchRecipes.dummyRecipe
        case Left(value) => throw new IllegalStateException(value)
      }
    }

    override def read(recipeId: ResourceLocation, buffer: PacketBuffer): WorkbenchRecipes = {
      val location = buffer.readResourceLocation()
      val output = buffer.readItemStack()
      val energy = buffer.readLong()
      val showInJEI = buffer.readBoolean()

      val recipeSize = buffer.readVarInt()
      val builder = Seq.newBuilder[Seq[IngredientWithCount]]
      for (_ <- 0 until recipeSize) {
        val b2 = Seq.newBuilder[IngredientWithCount]
        val size = buffer.readVarInt()
        for (_ <- 0 until size) {
          b2 += IngredientWithCount.readFromBuffer(buffer)
        }
        builder += b2.result()
      }
      new IngredientRecipe(location, output, energy, showInJEI, builder.result())
    }

    override def write(buffer: PacketBuffer, recipe: WorkbenchRecipes): Unit = {
      buffer.writeResourceLocation(recipe.location)
      buffer.writeItemStack(recipe.getOutput)
      buffer.writeLong(recipe.energy)
      buffer.writeBoolean(recipe.showInJEI)

      buffer.writeVarInt(recipe.size)
      recipe.inputs.foreach { s =>
        buffer.writeVarInt(s.size)
        s.foreach(_.writeToBuffer(buffer))
      }
    }

    override def getName = recipeLocation
  }

}