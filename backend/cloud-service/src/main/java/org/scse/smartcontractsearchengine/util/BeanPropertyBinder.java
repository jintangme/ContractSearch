package org.scse.smartcontractsearchengine.util;

import java.lang.reflect.Field;
import java.util.Map;

public class BeanPropertyBinder {

    /**
     * 根据字段名动态设置对象属性值
     * @param target 目标对象
     * @param fieldName 字段名（支持驼峰和下划线）
     * @param value 字段值
     */
    public static void setField(Object target, String fieldName, Object value) {
        if (value == null) {
            return;
        }

        // 转换为驼峰命名
        String camelCaseName = toCamelCase(fieldName);

        try {
            Field field = target.getClass().getDeclaredField(camelCaseName);
            field.setAccessible(true);

            // 类型转换
            Object convertedValue = convertType(value, field.getType());
            field.set(target, convertedValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 忽略不存在的字段
        }
    }

    /**
     * 下划线转驼峰
     */
    private static String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        boolean upperCase = false;

        for (char c : str.toCharArray()) {
            if (c == '_') {
                upperCase = true;
            } else {
                result.append(upperCase ? Character.toUpperCase(c) : Character.toLowerCase(c));
                upperCase = false;
            }
        }

        return result.toString();
    }

    /**
     * 类型转换
     */
    private static Object convertType(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }

        if (value == null) {
            return null;
        }

        String strValue = value.toString();

        if (targetType == String.class) {
            return strValue;
        } else if (targetType == Integer.class || targetType == int.class) {
            return Integer.valueOf(strValue);
        } else if (targetType == Long.class || targetType == long.class) {
            return Long.valueOf(strValue);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.valueOf(strValue);
        } else if (targetType == Float.class || targetType == float.class) {
            return Float.valueOf(strValue);
        } else if (targetType == Double.class || targetType == double.class) {
            return Double.valueOf(strValue);
        }

        return strValue;
    }
}
