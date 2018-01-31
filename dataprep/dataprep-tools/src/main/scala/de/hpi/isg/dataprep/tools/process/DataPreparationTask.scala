package de.hpi.isg.dataprep.tools.process

import java.util.regex.Pattern
import java.security.MessageDigest

import org.apache.spark.sql.expressions.{UserDefinedFunction, Window}
import org.apache.spark.sql.{Column, DataFrame, Row}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

/**
  * @author Lan Jiang
  * @since 17/01/2018
  */
class DataPreparationTask(df: DataFrame) {

  private var dataFrame = df

  private val copied_column_suffix = "_copied"

//  def replaceSubstring(columnName: String, sourceValue: String, targetValue: String, k: Int=0): DataFrame = {
//    var column = regexp_replace(dataFrame.col(columnName), sourceValue, targetValue)
//    dataFrame
//  }

  def getDataFrame(): DataFrame = {
    dataFrame
  }

  def renameColumn(columnName: String, newColumnName: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumnRenamed(columnName, newColumnName)
    this
  }

  def addColumn(columnName: String, position: Int): DataPreparationTask = {
    this.addColumnWithDefaultValue(columnName, position, null)
  }

  def addColumnWithDefaultValue(columnName: String, position: Int, defaultValue: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumn(columnName, lit(defaultValue))
    val columns = dataFrame.columns
    dataFrame = changeColumnPosition(columns, position)
    this
  }

  def copyColumn(columnName: String): DataPreparationTask = {
    val position = dataFrame.schema.fieldIndex(columnName) + 1
    copyColumn(columnName, position)
  }

  def copyColumn(columnName: String, position: Int): DataPreparationTask = {
    val copiedColumnName = columnName + copied_column_suffix
    dataFrame = dataFrame.withColumn(copiedColumnName, dataFrame.col(columnName))
    val columns = dataFrame.columns
    dataFrame = changeColumnPosition(columns, position)
    this
  }

  def moveColumn(columnName: String, newPosition: Int): DataPreparationTask = {
    // check whether the newPosition is out of range

    val currentPosition = dataFrame.schema.fieldIndex(columnName)
    if (currentPosition < newPosition) {
      copyColumn(columnName, newPosition)
    } else {
      copyColumn(columnName, newPosition - 1)
    }
    this.deleteColumn(columnName)
    val copied_name = columnName + copied_column_suffix
    this.renameColumn(copied_name, columnName)
  }

  def moveColumnToHead(columnName: String): DataPreparationTask = {
    moveColumn(columnName, 1)
  }

  def moveColumnToTail(columnName: String): DataPreparationTask = {
    val schemaLength = dataFrame.schema.fields.length
    moveColumn(columnName, schemaLength)
  }

  def deleteColumn(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.drop(columnName)
    this
  }

  def sortByColumnAscending(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.sort(dataFrame.col(columnName).asc)
    this
  }

  def sortByColumnDescending(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.sort(dataFrame.col(columnName).desc)
    this
  }

  def replaceConstSubstring(columnName: String, sourceValue: String, targetValue: String, k: Int=0): DataPreparationTask = {
    // if k is zero, replace all the found substring with the new string
    dataFrame = dataFrame.withColumn(columnName, regexp_replace(col(columnName), sourceValue, targetValue))
    this
  }

  def replaceSubstringWithRegularExpression(columnName: String, pattern: Pattern, targetValue: String, k: Int=0): DataPreparationTask = {
    // if k is zero, replace all the found substring with the new string
    dataFrame = dataFrame.withColumn(columnName, regexp_replace(col(columnName), pattern.pattern(), targetValue))
    this
  }

  def removeNumericCharacters(columnName: String): DataPreparationTask = {
    val pattern = Pattern.compile("\\d+")
    this.replaceSubstringWithRegularExpression(columnName, pattern, "")
  }

  def removeNumericCharacters(): DataPreparationTask = {
    dataFrame.columns.map(columnName => removeNumericCharacters(columnName))
    this
  }

  def removeNonNumerics(columnName: String): DataPreparationTask = {
    val pattern = Pattern.compile("[^0-9]")
    this.replaceSubstringWithRegularExpression(columnName, pattern, "")
  }

  def removeNonNumerics(): DataPreparationTask = {
    dataFrame.columns.map(columnName => removeNonNumerics(columnName))
    this
  }

  def removeNonAlphaNumerics(columnName: String): DataPreparationTask = {
    val pattern = Pattern.compile("[^a-zA-Z0-9]")
    this.replaceSubstringWithRegularExpression(columnName, pattern, "")
  }

  def removeNonAlphaNumerics(): DataPreparationTask = {
    dataFrame.columns.map(columnName => removeNonAlphaNumerics(columnName))
    this
  }

  def removeString(columnName: String, toBeRemovedString: String): DataPreparationTask = {
    this.replaceConstSubstring(columnName, toBeRemovedString, "")
  }

  def removeString(toBeRemovedString: String): DataPreparationTask = {
    dataFrame.columns.map(columnName => removeString(columnName, toBeRemovedString))
    this
  }

  def fillNull(columnName: String, filling: String): DataPreparationTask = {
    // now consider the build-in null, in the future read the null value from metadata
    dataFrame = dataFrame.withColumn(columnName, when(dataFrame.col(columnName).isNull, filling).otherwise(col(columnName)))
    this
  }

  def fillDownNull(columnName: String): DataPreparationTask = {
    //df.withColumn("id", func.last('id', True).over(Window.partitionBy('session').orderBy('ts').rowsBetween(-sys.maxsize, 0))).show()

    this
  }

  def hash(columnName: String, algorithm: String): DataPreparationTask = {
    val caseInsensitiveAlgorithmName = algorithm.toUpperCase
    var newColumnName = columnName
    caseInsensitiveAlgorithmName match {
      case "MD5" => {
        newColumnName += "_md5"
        dataFrame = dataFrame.withColumn(newColumnName, md5(dataFrame.col(columnName)))
      }
      case "SHA1" => {
        newColumnName += "_sha1"
        dataFrame = dataFrame.withColumn(newColumnName, sha1(dataFrame.col(columnName)))
      }
    }
    val position = dataFrame.schema.fieldIndex(columnName) + 1
    moveColumn(newColumnName, position)
    this
  }

  def phoneticHash(columnName: String, algorithm: String): DataPreparationTask = {
    val caseInsensitiveAlgorithmName = algorithm.toUpperCase
    var newColumnName = columnName
    caseInsensitiveAlgorithmName match {
      case "SOUNDEX" => {
        newColumnName += "_soundex"
        dataFrame = dataFrame.withColumn(newColumnName, soundex(dataFrame.col(columnName).cast(StringType)))
      }
    }
    this
  }

  def lowerCase(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumn(columnName, lower(dataFrame.col(columnName)))
    this
  }

  def upperCase(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumn(columnName, upper(dataFrame.col(columnName)))
    this
  }

  def letterCase(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumn(columnName, initcap(dataFrame.col(columnName)))
    this
  }

  def trimColumn(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.withColumn(columnName, trim(col(columnName)))
    this
  }

  def addAutoIncreasingIdColumn(): DataPreparationTask = {
    val columnName = "auto_increasing_id"
    try {
      val fieldIndex = dataFrame.schema.fieldIndex(columnName)
      println("The auto_increasing_id column already exists!")
    } catch {
      case ex: IllegalArgumentException => {
        dataFrame = dataFrame.withColumn(columnName, monotonically_increasing_id())
        moveColumnToHead(columnName)
      }
    }
    this
  }

  def changeColumnDataType(columnName: String, dataType: DataType): DataPreparationTask = {
    val currentDataType = dataFrame.dtypes
      .filter(tuple => tuple._1.equals(columnName))
      .map(tuple => tuple._2).head
    if (dataType.isInstanceOf[NumericType]) {
      dataFrame = dataFrame.withColumn(columnName, dataFrame.col(columnName).cast(dataType))
    } else if (dataType.isInstanceOf[StringType]) {
      dataFrame = dataFrame.withColumn(columnName, dataFrame.col(columnName).cast(dataType))
    } else {

    }
    this
  }

  def forContrast(columnName: String): DataPreparationTask = {
    dataFrame = dataFrame.select(dataFrame.col(columnName).alias("Name"), md5(dataFrame.col(columnName).cast(StringType)).alias("New_Name"))
    this
  }

  private def changeColumnPosition(columns: Array[String], position: Int): DataFrame = {
    val headPart = columns.slice(0, position)
    val tailPart = columns.slice(position, columns.length - 1)
    val reorderedColumnNames = headPart ++ columns.slice(columns.length - 1, columns.length) ++ tailPart
    dataFrame.select(reorderedColumnNames.map(column => col(column)): _*)
  }
}
