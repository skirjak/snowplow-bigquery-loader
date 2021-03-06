/*
 * Copyright (c) 2018-2020 Snowplow Analytics Ltd. All rights reserved.
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
package com.snowplowanalytics.snowplow.storage.bigquery.mutator

import com.google.cloud.bigquery.{Schema => BqSchema, _}

import scala.collection.JavaConverters._
import cats.effect.IO

import com.snowplowanalytics.snowplow.storage.bigquery.common.Adapter

/** Stateless object, responsible for making API calls to a table or mock */
trait TableReference {

  /** Get all fields the table has */
  def getFields: IO[Vector[Field]]

  /** Add new field */
  def addField(field: Field): IO[Unit]
}

object TableReference {

  /** Real-world BigQuery implementation */
  class BigQueryTable(client: BigQuery, datasetId: String, tableId: String) extends TableReference {

    def getTable: IO[Table] =
      IO(client.getTable(datasetId, tableId)).flatMap {
        case null  => IO.raiseError(new RuntimeException(s"Table $tableId does not exist. Use 'mutator create'"))
        case table => IO.pure(table)
      }

    def getFields: IO[Vector[Field]] =
      getTable.map(BigQueryTable.getFields)

    def addField(field: Field): IO[Unit] =
      for {
        table <- getTable
        originalFields    = BigQueryTable.getFields(table)
        newSchema         = BqSchema.of((originalFields :+ field).asJava)
        updatedDefinition = table.getDefinition[StandardTableDefinition].toBuilder.setSchema(newSchema).build()
        updatedTable      = table.toBuilder.setDefinition(updatedDefinition).build()
        _ <- IO(updatedTable.update())
      } yield ()
  }

  object BigQueryTable {
    def getClient: IO[BigQuery] =
      IO(BigQueryOptions.getDefaultInstance.getService)

    def create(client: BigQuery, datasetId: String, tableId: String): IO[Table] = IO {
      val id         = TableId.of(datasetId, tableId)
      val schema     = BqSchema.of(Atomic.table.map(Adapter.adaptField).asJava)
      val definition = StandardTableDefinition.newBuilder().setSchema(schema).build()
      val tableInfo  = TableInfo.newBuilder(id, definition).build()
      client.create(tableInfo)
    }

    private def getFields(table: Table): Vector[Field] =
      table.getDefinition[StandardTableDefinition].getSchema.getFields.iterator().asScala.toVector
  }
}
