package org.scse.smartcontractsearchengine.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.StringJoiner;

@MappedTypes(float[].class)
public class VectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        // 将 float[] 转换为 PostgreSQL 的 vector 字符串表示，如 '[0.1,0.2,0.3]'
        // 使用 StringJoiner 构建向量字符串，格式为 [0.1,0.2,0.3]
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float v : parameter) {
            joiner.add(String.valueOf(v));
        }
        String vectorStr = joiner.toString();

        PGobject pgObject = new PGobject();
        pgObject.setType("vector");   // 指定类型为 vector
        pgObject.setValue(vectorStr);
        ps.setObject(i, pgObject);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseVector(rs.getString(columnName));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseVector(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseVector(cs.getString(columnIndex));
    }

    private float[] parseVector(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        // 去除首尾的方括号，按逗号分割
        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}