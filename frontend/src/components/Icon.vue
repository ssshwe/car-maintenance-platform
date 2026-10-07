<script setup>
import {
  PhSquaresFour, PhShoppingCart, PhReceipt, PhCalendarDots, PhStorefront,
  PhCarProfile, PhArrowRight, PhCaretDown, PhMagnifyingGlass, PhCheck,
  PhClock, PhUser, PhMapPin, PhPlus, PhMinus, PhX, PhTrash, PhPencilSimple,
  PhShieldCheck, PhWrench, PhQrCode, PhSignOut, PhStar, PhHeadset, PhTruck
} from '@phosphor-icons/vue'

defineProps({ name: String, size: { type: Number, default: 20 }, weight: { type: String, default: 'regular' } })
const icons = {
  grid: PhSquaresFour, bag: PhShoppingCart, orders: PhReceipt, calendar: PhCalendarDots,
  store: PhStorefront, car: PhCarProfile, arrow: PhArrowRight, down: PhCaretDown,
  search: PhMagnifyingGlass, check: PhCheck, clock: PhClock, user: PhUser, pin: PhMapPin,
  plus: PhPlus, minus: PhMinus, close: PhX, trash: PhTrash, edit: PhPencilSimple,
  shield: PhShieldCheck, wrench: PhWrench, qr: PhQrCode, logout: PhSignOut,
  star: PhStar, headset: PhHeadset, truck: PhTruck
}
</script>
<template><component :is="icons[name] || PhSquaresFour" :size="size" :weight="weight" aria-hidden="true" /></template>
