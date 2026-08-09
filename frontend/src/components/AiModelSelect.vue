<template>
  <label class="ai-model-select">
    <span v-if="label">{{ label }}</span>
    <select :value="modelValue" @change="emit('update:modelValue', $event.target.value)">
      <option v-for="option in AI_MODEL_OPTIONS" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>
  </label>
</template>

<script setup>
import { AI_MODEL_OPTIONS } from '@/config/aiModels'

defineProps({
  modelValue: {
    type: String,
    required: true
  },
  label: {
    type: String,
    default: '模型'
  }
})

const emit = defineEmits(['update:modelValue'])
</script>

<style scoped>
.ai-model-select {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #98a2b3;
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.ai-model-select select {
  min-width: 132px;
  height: 32px;
  padding: 0 30px 0 10px;
  border: 1px solid #dfe3e8;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  font-size: 13px;
  font-weight: 500;
  outline: none;
}
</style>
