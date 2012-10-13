/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.sql.pg;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PostgresJavaMethodResultsOutputter extends PostgresOutputter<Object[]>
{
    private Method method;

    public PostgresJavaMethodResultsOutputter(PostgresQueryContext context,
                                              PostgresJavaMethod statement) {
        super(context, statement);
        this.method = statement.getMethod();
    }

    @Override
    public void output(Object[] row, boolean usePVals) throws IOException {
        messenger.beginMessage(PostgresMessages.DATA_ROW_TYPE.code());
        messenger.writeShort(ncols);
        Class<?>[] parameterTypes = method.getParameterTypes();
        int fieldIndex = 0;
        for (int paramIndex = 0; paramIndex < parameterTypes.length; paramIndex++) {
            if (!parameterTypes[paramIndex].isArray()) continue;
            Object field = Array.get(row[paramIndex], 0);
            PostgresType type = columnTypes.get(fieldIndex);
            boolean binary = context.isColumnBinary(fieldIndex);
            ByteArrayOutputStream bytes;
            if (usePVals) bytes = encoder.encodePObject(field, type, binary);
            else bytes = encoder.encodeObject(field, type, binary);
            if (field == null) {
                messenger.writeInt(-1);
            }
            else {
                messenger.writeInt(bytes.size());
                messenger.writeByteStream(bytes);
            }
            fieldIndex++;
        }
        messenger.sendMessage();
    }

}