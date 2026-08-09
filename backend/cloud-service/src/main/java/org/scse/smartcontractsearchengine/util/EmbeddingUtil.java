package org.scse.smartcontractsearchengine.util;

import com.openai.models.embeddings.Embedding;

import java.util.ArrayList;
import java.util.List;

public class EmbeddingUtil {

    /**
     * 将Embedding转换为float数组
     *
     * @param embeddings Embedding列表
     * @return float数组列表
     */
    public static List<float[]> embedding2float(List<Embedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return new ArrayList<>();
        }

        List<float[]> floatArrays = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            List<Float> embeddingVector = embedding.embedding();
            float[] floatArray = CollectionUtil.toFloatArray(embeddingVector);
            floatArrays.add(floatArray);
        }

        return floatArrays;
    }

    /**
     * 手动截断降维
     * @param embedding
     * @param dimension
     * @return
     */
    public static float[] toLowerDimension(float[] embedding, int dimension) {
        float[] lowDimensionEmbedding = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            lowDimensionEmbedding[i] = embedding[i];
        }
        return lowDimensionEmbedding;
    }

    public static float[] toLowerDimension(float[] embedding) {
        return toLowerDimension(embedding, 768);
    }

    public static List<float[]> toLowerDimension(List<float[]> embeddings, int dimension) {
        List<float[]> lowDimensionEmbeddings = new ArrayList<>(embeddings.size());
        for (float[] embedding : embeddings) {
            lowDimensionEmbeddings.add(toLowerDimension(embedding, dimension));
        }
        return lowDimensionEmbeddings;
    }

    public static List<float[]> toLowerDimension(List<float[]> embeddings) {
        return toLowerDimension(embeddings, 768);
    }

}
