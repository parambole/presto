/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.prestosql.plugin.google.sheets;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import io.airlift.slice.Slice;
import io.prestosql.spi.Page;
import io.prestosql.spi.PrestoException;
import io.prestosql.spi.block.Block;
import io.prestosql.spi.connector.ConnectorPageSink;
import io.prestosql.spi.type.BigintType;
import io.prestosql.spi.type.DateType;
import io.prestosql.spi.type.DoubleType;
import io.prestosql.spi.type.IntegerType;
import io.prestosql.spi.type.SmallintType;
import io.prestosql.spi.type.TimeType;
import io.prestosql.spi.type.TimestampType;
import io.prestosql.spi.type.TinyintType;
import io.prestosql.spi.type.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.prestosql.spi.StandardErrorCode.NOT_SUPPORTED;
import static io.prestosql.spi.type.Varchars.isVarcharType;
import static java.lang.Math.toIntExact;
import static java.util.concurrent.CompletableFuture.completedFuture;

public class SheetsPageSink
        implements ConnectorPageSink
{
    private final SheetsClient sheetsClient;
    private final String table;
    private final List<SheetsColumnHandle> columns;

    public SheetsPageSink(SheetsClient sheetsClient, String table,
                          List<SheetsColumnHandle> columns)
    {
        this.sheetsClient = sheetsClient;
        this.table = table;
        this.columns = columns;
    }

    @Override
    public CompletableFuture<?> appendPage(Page page)
    {
        List<List<Object>> rows = new ArrayList<>();
        for (int position = 0; position < page.getPositionCount(); position++) {
            List<Object> row = new ArrayList<>();
            for (int channel = 0; channel < page.getChannelCount(); channel++) {
                row.add(getObjectValue(columns.get(channel).getColumnType(),
                        page.getBlock(channel), position));
            }
            rows.add(row);
        }
        String sheetId = sheetsClient.getCachedSheetExpressionForTable(table);
        sheetsClient.insertIntoSheet(sheetId, rows);
        return NOT_BLOCKED;
    }

    private Object getObjectValue(Type type, Block block, int position)
    {
        if (type.equals(BigintType.BIGINT)) {
            return type.getLong(block, position);
        }
        if (type.equals(IntegerType.INTEGER)) {
            return toIntExact(type.getLong(block, position));
        }
        if (type.equals(SmallintType.SMALLINT)) {
            return Shorts.checkedCast(type.getLong(block, position));
        }
        if (type.equals(TinyintType.TINYINT)) {
            return SignedBytes.checkedCast(type.getLong(block, position));
        }
        if (type.equals(DoubleType.DOUBLE)) {
            return type.getDouble(block, position);
        }
        if (isVarcharType(type)) {
            return type.getSlice(block, position).toStringUtf8();
        }
        if (type.equals(DateType.DATE)) {
            long days = type.getLong(block, position);
            return new Date(TimeUnit.DAYS.toMillis(days));
        }
        if (type.equals(TimeType.TIME)) {
            long millisUtc = type.getLong(block, position);
            return new Date(millisUtc);
        }
        if (type.equals(TimestampType.TIMESTAMP)) {
            long millisUtc = type.getLong(block, position);
            return new Date(millisUtc);
        }
        throw new PrestoException(NOT_SUPPORTED, "unsupported type: " + type);
    }

    @Override
    public CompletableFuture<Collection<Slice>> finish()
    {
        return completedFuture(ImmutableList.of());
    }

    @Override
    public void abort() {}
}