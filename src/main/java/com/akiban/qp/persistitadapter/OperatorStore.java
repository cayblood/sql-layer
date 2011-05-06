/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.qp.persistitadapter;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.GroupTable;
import com.akiban.ais.model.Index;
import com.akiban.ais.model.UserTable;
import com.akiban.qp.expression.IndexBound;
import com.akiban.qp.expression.IndexKeyRange;
import com.akiban.qp.physicaloperator.API;
import com.akiban.qp.physicaloperator.ConstantValueBindable;
import com.akiban.qp.physicaloperator.Cursor;
import com.akiban.qp.physicaloperator.GroupCursor;
import com.akiban.qp.physicaloperator.NoLimit;
import com.akiban.qp.physicaloperator.PhysicalOperator;
import com.akiban.qp.physicaloperator.UpdateLambda;
import com.akiban.qp.physicaloperator.Update_Default;
import com.akiban.qp.physicaloperator.WrappingPhysicalOperator;
import com.akiban.qp.row.HKey;
import com.akiban.qp.row.OverlayingRow;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.Schema;
import com.akiban.server.RowData;
import com.akiban.server.RowDef;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.server.api.dml.ConstantColumnSelector;
import com.akiban.server.api.dml.scan.NewRow;
import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.service.session.Session;
import com.akiban.server.store.DelegatingStore;
import com.akiban.server.store.PersistitStore;
import com.persistit.Persistit;

import static com.akiban.qp.physicaloperator.API.emptyBindings;
import static com.akiban.qp.physicaloperator.API.indexLookup_Default;
import static com.akiban.qp.physicaloperator.API.indexScan_Default;

public final class OperatorStore extends DelegatingStore<PersistitStore> {

    // DelegatingStore interface

    public OperatorStore() {
        super(new PersistitStore());
    }

    // OperatorStore interface

    public PersistitStore getPersistitStore() {
        return super.getDelegate();
    }

    // Store interface

    @Override
    public void updateRow(Session session, RowData oldRowData, RowData newRowData, ColumnSelector columnSelector)
            throws Exception
    {
        PersistitStore persistitStore = getPersistitStore();
        AkibanInformationSchema ais = persistitStore.getRowDefCache().ais();
        Schema schema = new Schema(ais);
        PersistitAdapter adapter = new PersistitAdapter(schema, persistitStore, session);

        PersistitGroupRow oldRow = PersistitGroupRow.newPersistitGroupRow(adapter, oldRowData);
        RowDef rowDef = persistitStore.getRowDefCache().rowDef(oldRowData.getRowDefId());
        UpdateLambda updateLambda = new InternalUpdateLambda(adapter, rowDef, newRowData, columnSelector);

        UserTable userTable = ais.getUserTable(oldRowData.getRowDefId());
        GroupTable groupTable = userTable.getGroup().getGroupTable();
        IndexBound bound = new IndexBound(userTable, oldRow, new ConstantColumnSelector(true));
        IndexKeyRange range = new IndexKeyRange(bound, true, bound, true);

        final PhysicalOperator scanOp;
        if (userTable == groupTable.getRoot()) {
            scanOp = API.groupScan_Default(groupTable, false, NoLimit.instance(), ConstantValueBindable.of(range));
        }
        else {
            Index index = getBestIndex(userTable);
            PhysicalOperator indexScan = indexScan_Default(index, false, ConstantValueBindable.of(range));
            scanOp = indexLookup_Default(indexScan, groupTable);
        }

        Update_Default updateOp = new Update_Default(scanOp, ConstantValueBindable.of(updateLambda));

        Cursor updateCursor = emptyBindings(adapter, updateOp);
        updateCursor.open();
        try {
            if (!updateCursor.next()) {
                throw new RuntimeException("no next!");
            }
        } finally {
            updateCursor.close();
        }
    }

    private static Index getBestIndex(UserTable userTable) {
        Index pk = userTable.getIndex("PRIMARY");
        if (pk != null) {
            return pk;
        }
        throw new UnsupportedOperationException();
    }

    private static class InternalUpdateLambda implements UpdateLambda {
        private final PersistitAdapter adapter;
        private final RowData newRowData;
        private final ColumnSelector columnSelector;
        private final RowDef rowDef;

        private InternalUpdateLambda(PersistitAdapter adapter, RowDef rowDef, RowData newRowData, ColumnSelector columnSelector) {
            this.newRowData = newRowData;
            this.columnSelector = columnSelector;
            this.rowDef = rowDef;
            this.adapter = adapter;
        }

        @Override
        public boolean rowIsApplicable(Row row) {
            return (row instanceof PersistitGroupRow)
                    && ((PersistitGroupRow)row).rowDef().getRowDefId() == rowDef.getRowDefId();
        }

        @Override
        public Row applyUpdate(Row original) {
            // TODO
            // ideally we'd like to use an OverlayingRow, but ModifiablePersistitGroupCursor requires
            // a PersistitGroupRow if an hkey changes
//            OverlayingRow overlay = new OverlayingRow(original);
//            for (int i=0; i < rowDef.getFieldCount(); ++i) {
//                if (columnSelector == null || columnSelector.includesColumn(i)) {
//                    overlay.overlay(i, newRowData.toObject(rowDef, i));
//                }
//            }
//            return overlay;
            NewRow newRow = new NiceRow(rowDef.getRowDefId());
            for (int i=0; i < original.rowType().nFields(); ++i) {
                if (columnSelector == null || columnSelector.includesColumn(i)) {
                    newRow.put(i, newRowData.toObject(rowDef, i));
                }
                else {
                    newRow.put(i, original.field(i));
                }
            }
            return PersistitGroupRow.newPersistitGroupRow(adapter, newRow.toRowData());
        }
    }
}
