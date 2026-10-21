import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const LEARNING_KEY = 'xy_learning_mode'

export const useUiStore = defineStore('ui', () => {
  const learningMode = ref(localStorage.getItem(LEARNING_KEY) !== 'off')

  watch(learningMode, (value) => {
    localStorage.setItem(LEARNING_KEY, value ? 'on' : 'off')
  })

  function toggleLearning() {
    learningMode.value = !learningMode.value
  }

  return { learningMode, toggleLearning }
})
