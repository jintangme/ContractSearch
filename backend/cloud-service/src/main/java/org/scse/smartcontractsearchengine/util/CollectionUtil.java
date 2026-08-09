package org.scse.smartcontractsearchengine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CollectionUtil {
    private static final Logger logger = LoggerFactory.getLogger(CollectionUtil.class);

    /**
     * 将 List<Double> 转换为 float[]
     * @param numberList 数字列表（Double 或 Float）
     * @return 单精度浮点数数组
     */
    public static float[] toFloatArray(List<? extends Number> numberList) {
        if (numberList == null || numberList.isEmpty()) {
            return new float[0];
        }

        float[] floatArray = new float[numberList.size()];
        for (int i = 0; i < numberList.size(); i++) {
            Number value = numberList.get(i);
            floatArray[i] = (value != null) ? value.floatValue() : 0.0f;
        }

        return floatArray;
    }
}
