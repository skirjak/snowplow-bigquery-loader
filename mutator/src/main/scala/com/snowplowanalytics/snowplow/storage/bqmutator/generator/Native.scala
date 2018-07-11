/*
 * Copyright (c) 2018 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.storage.bqmutator.generator

import scala.collection.convert.decorateAsJava._

import com.google.cloud.bigquery.{Field, FieldList, LegacySQLTypeName}

import Generator._

// This must not be backported to Schema DDL, as a single module
// dependent on Google SDK
object Native {

  def toField(column: Column): Field =
    getField(column.bigQueryField)(column.name)

  private def getField(bigQueryField: BigQueryField): String => Field =
    bigQueryField match {
      case BigQueryField(r @ BigQueryType.Record(fields), m) =>
        val subFields = fields.toList.map { case (name, field) => getField(field)(name) }
        val fieldsList = FieldList.of(subFields.asJava)
        name => Field.newBuilder(name, typeTo(r), fieldsList).setMode(modeTo(m)).build()
      case BigQueryField(t, m @ FieldMode.Repeated) =>
        name => getField(BigQueryField(t, m))(name)
      case BigQueryField(t, m) =>
        name => Field.newBuilder(name, typeTo(t)).setMode(modeTo(m)).build()
    }

  def modeTo(fieldMode: FieldMode): Field.Mode =
    fieldMode match {
      case FieldMode.Nullable => Field.Mode.NULLABLE
      case FieldMode.Required => Field.Mode.REQUIRED
      case FieldMode.Repeated => Field.Mode.REPEATED
    }

  def typeTo(fieldType: BigQueryType): LegacySQLTypeName =
    fieldType match {
      case BigQueryType.DateTime => LegacySQLTypeName.DATETIME
      case BigQueryType.Integer => LegacySQLTypeName.INTEGER
      case BigQueryType.Boolean => LegacySQLTypeName.BOOLEAN
      case BigQueryType.String => LegacySQLTypeName.STRING
      case BigQueryType.Float => LegacySQLTypeName.FLOAT
      case BigQueryType.Bytes => LegacySQLTypeName.BYTES
      case BigQueryType.Date => LegacySQLTypeName.DATE
      case BigQueryType.Record(_) => LegacySQLTypeName.RECORD
    }
}