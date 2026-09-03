<template>
  <span class="tool-glyph" :class="[`is-${status}`, `kind-${kind}`]" aria-hidden="true">
    <span v-if="status === 'error'" class="icon-error"></span>
    <span v-else-if="status === 'completed'" class="icon-done"></span>
    <span v-else class="icon-run" :class="`icon-${kind}`">
      <template v-if="kind === 'search'">
        <i class="lens"></i>
        <i class="handle"></i>
      </template>
      <template v-else-if="kind === 'extract'">
        <i class="page"></i>
        <i class="scan"></i>
      </template>
      <template v-else-if="kind === 'code'">
        <i class="chev left"></i>
        <i class="chev right"></i>
      </template>
      <template v-else-if="kind === 'terminal'">
        <i class="box"></i>
        <i class="caret"></i>
      </template>
      <template v-else-if="kind === 'browser'">
        <i class="globe"></i>
        <i class="eq"></i>
        <i class="mer"></i>
      </template>
      <template v-else-if="kind === 'file'">
        <i class="folder"></i>
        <i class="tab"></i>
      </template>
      <template v-else-if="kind === 'vision'">
        <i class="eye"></i>
        <i class="pupil"></i>
      </template>
      <template v-else-if="kind === 'image'">
        <i class="frame"></i>
        <i class="sun"></i>
      </template>
      <template v-else-if="kind === 'video'">
        <i class="play"></i>
      </template>
      <template v-else-if="kind === 'memory'">
        <i class="bar a"></i>
        <i class="bar b"></i>
        <i class="bar c"></i>
      </template>
      <template v-else-if="kind === 'todo'">
        <i class="box"></i>
        <i class="tick"></i>
      </template>
      <template v-else-if="kind === 'speak'">
        <i class="wave a"></i>
        <i class="wave b"></i>
        <i class="wave c"></i>
      </template>
      <template v-else-if="kind === 'agent'">
        <i class="dot a"></i>
        <i class="dot b"></i>
        <i class="link"></i>
      </template>
      <template v-else-if="kind === 'skill'">
        <i class="badge"></i>
        <i class="star"></i>
        <i class="spark a"></i>
        <i class="spark b"></i>
      </template>
      <template v-else-if="kind === 'clock'">
        <i class="dial"></i>
        <i class="hand"></i>
      </template>
      <template v-else-if="kind === 'ask'">
        <i class="mark"></i>
      </template>
      <template v-else-if="kind === 'computer'">
        <i class="screen"></i>
        <i class="stand"></i>
      </template>
      <template v-else-if="kind === 'home'">
        <i class="roof"></i>
        <i class="body"></i>
      </template>
      <template v-else-if="kind === 'social'">
        <i class="bubble"></i>
      </template>
      <template v-else>
        <i class="cog"></i>
      </template>
    </span>
  </span>
</template>

<script setup lang="ts">
defineProps<{
  kind: string
  status: 'running' | 'completed' | 'error'
}>()
</script>

<style scoped lang="scss">
.tool-glyph {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.icon-run {
  position: relative;
  width: 16px;
  height: 16px;
}

.icon-done {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: linear-gradient(180deg, #22c55e, #16a34a);
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.16);
  animation: tool-done-pop 0.45s cubic-bezier(0.2, 0.8, 0.2, 1) both;

  &::after {
    content: '';
    display: block;
    width: 4px;
    height: 7px;
    margin: 2px auto 0;
    border-right: 2px solid #fff;
    border-bottom: 2px solid #fff;
    transform: rotate(45deg);
  }
}

.icon-error {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #dc2626;
  box-shadow: 0 0 0 3px rgba(220, 38, 38, 0.18);
}

.icon-search {
  animation: tool-search-bob 1.2s ease-in-out infinite;

  .lens {
    position: absolute;
    top: 1px;
    left: 1px;
    width: 10px;
    height: 10px;
    border: 2px solid #3866f5;
    border-radius: 50%;
    box-sizing: border-box;
    animation: tool-search-pulse 1.2s ease-in-out infinite;
  }

  .handle {
    position: absolute;
    right: 1px;
    bottom: 1px;
    width: 6px;
    height: 2px;
    border-radius: 1px;
    background: #3866f5;
    transform: rotate(45deg);
    transform-origin: left center;
  }
}

.icon-extract {
  .page {
    position: absolute;
    inset: 1px 3px 1px 3px;
    border: 1.5px solid #3866f5;
    border-radius: 2px;
  }

  .scan {
    position: absolute;
    left: 4px;
    right: 4px;
    height: 1.5px;
    background: #36a9ff;
    animation: tool-scan 1.1s ease-in-out infinite;
  }
}

.icon-code {
  .chev {
    position: absolute;
    top: 4px;
    width: 6px;
    height: 8px;
    border: solid #3866f5;
    border-width: 0 2px 2px 0;
    box-sizing: border-box;
  }

  .left {
    left: 2px;
    transform: rotate(135deg);
    animation: tool-code-in 1s ease-in-out infinite;
  }

  .right {
    right: 2px;
    transform: rotate(-45deg);
    animation: tool-code-in 1s ease-in-out infinite reverse;
  }
}

.icon-terminal {
  .box {
    position: absolute;
    inset: 1px;
    border: 1.5px solid #3866f5;
    border-radius: 2px;
  }

  .caret {
    position: absolute;
    left: 4px;
    bottom: 4px;
    width: 5px;
    height: 2px;
    background: #3866f5;
    animation: tool-blink 0.9s steps(2) infinite;
  }
}

.icon-browser {
  animation: tool-spin-slow 2.4s linear infinite;

  .globe {
    position: absolute;
    inset: 1px;
    border: 1.5px solid #3866f5;
    border-radius: 50%;
  }

  .eq {
    position: absolute;
    left: 2px;
    right: 2px;
    top: 7px;
    height: 1.5px;
    background: #3866f5;
  }

  .mer {
    position: absolute;
    top: 2px;
    bottom: 2px;
    left: 7px;
    width: 1.5px;
    background: #36a9ff;
  }
}

.icon-file {
  animation: tool-bob 1s ease-in-out infinite;

  .folder {
    position: absolute;
    left: 1px;
    right: 1px;
    top: 5px;
    bottom: 1px;
    border: 1.5px solid #3866f5;
    border-radius: 1px 2px 2px 2px;
  }

  .tab {
    position: absolute;
    left: 1px;
    top: 2px;
    width: 6px;
    height: 4px;
    border: 1.5px solid #3866f5;
    border-bottom: 0;
    border-radius: 2px 2px 0 0;
  }
}

.icon-vision {
  .eye {
    position: absolute;
    left: 1px;
    right: 1px;
    top: 4px;
    height: 8px;
    border: 1.5px solid #3866f5;
    border-radius: 50%;
  }

  .pupil {
    position: absolute;
    top: 6px;
    left: 6px;
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #3866f5;
    animation: tool-look 1.4s ease-in-out infinite;
  }
}

.icon-image {
  .frame {
    position: absolute;
    inset: 1px;
    border: 1.5px solid #3866f5;
    border-radius: 2px;
  }

  .sun {
    position: absolute;
    top: 3px;
    right: 3px;
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #36a9ff;
    animation: tool-twinkle 1s ease-in-out infinite;
  }
}

.icon-video {
  .play {
    position: absolute;
    top: 3px;
    left: 5px;
    border-style: solid;
    border-width: 5px 0 5px 8px;
    border-color: transparent transparent transparent #3866f5;
    animation: tool-pulse 1s ease-in-out infinite;
  }
}

.icon-memory {
  .bar {
    position: absolute;
    bottom: 2px;
    width: 3px;
    border-radius: 1px;
    background: #3866f5;
  }

  .a {
    left: 2px;
    animation: tool-eq 0.8s ease-in-out infinite;
  }

  .b {
    left: 6.5px;
    animation: tool-eq 0.8s ease-in-out 0.15s infinite;
  }

  .c {
    left: 11px;
    animation: tool-eq 0.8s ease-in-out 0.3s infinite;
  }
}

.icon-todo {
  .box {
    position: absolute;
    inset: 2px;
    border: 1.5px solid #3866f5;
    border-radius: 2px;
  }

  .tick {
    position: absolute;
    left: 5px;
    top: 3px;
    width: 4px;
    height: 7px;
    border-right: 2px solid #36a9ff;
    border-bottom: 2px solid #36a9ff;
    transform: rotate(45deg);
    animation: tool-tick 1.2s ease-in-out infinite;
  }
}

.icon-speak {
  .wave {
    position: absolute;
    bottom: 2px;
    width: 2px;
    border-radius: 1px;
    background: #3866f5;
  }

  .a {
    left: 3px;
    animation: tool-eq 0.7s ease-in-out infinite;
  }

  .b {
    left: 7px;
    animation: tool-eq 0.7s ease-in-out 0.12s infinite;
  }

  .c {
    left: 11px;
    animation: tool-eq 0.7s ease-in-out 0.24s infinite;
  }
}

.icon-agent {
  .dot {
    position: absolute;
    top: 6px;
    width: 4px;
    height: 4px;
    border-radius: 50%;
    background: #3866f5;
  }

  .a {
    left: 1px;
    animation: tool-node 1.2s ease-in-out infinite;
  }

  .b {
    right: 1px;
    animation: tool-node 1.2s ease-in-out 0.2s infinite;
  }

  .link {
    position: absolute;
    top: 7.5px;
    left: 5px;
    right: 5px;
    height: 1.5px;
    background: #36a9ff;
    transform-origin: left center;
    animation: tool-link 1.2s ease-in-out infinite;
  }
}

.icon-skill {
  .badge {
    position: absolute;
    inset: 1px;
    border-radius: 4px;
    background: linear-gradient(145deg, #a78bfa, #7c3aed 55%, #4f46e5);
    box-shadow: 0 0 0 2px rgba(124, 58, 237, 0.18);
  }

  .star {
    position: absolute;
    inset: 3px;
    background: linear-gradient(180deg, #fffbeb, #fde68a);
    clip-path: polygon(50% 8%, 61% 38%, 94% 38%, 68% 58%, 78% 90%, 50% 72%, 22% 90%, 32% 58%, 6% 38%, 39% 38%);
    animation: tool-twinkle 1.2s ease-in-out infinite;
  }

  .spark {
    position: absolute;
    width: 3px;
    height: 3px;
    border-radius: 50%;
    background: #f5d0fe;
  }

  .a {
    top: 1px;
    right: 1px;
    animation: tool-pulse 1s ease-in-out infinite;
  }

  .b {
    left: 1px;
    bottom: 2px;
    width: 2px;
    height: 2px;
    background: #ddd6fe;
    animation: tool-pulse 1s ease-in-out 0.2s infinite;
  }
}

.icon-gear {
  .cog {
    position: absolute;
    inset: 1px;
    border-radius: 50%;
    background:
      radial-gradient(circle at center, #fff7ed 0 28%, transparent 29%),
      conic-gradient(from 20deg, #fb923c, #ea580c, #64748b, #334155, #fb923c);
    animation: tool-spin-slow 1.6s linear infinite;
    box-shadow: inset 0 0 0 1.5px rgba(51, 65, 85, 0.35);
  }
}

.icon-clock {
  .dial {
    position: absolute;
    inset: 1px;
    border: 1.5px solid #3866f5;
    border-radius: 50%;
  }

  .hand {
    position: absolute;
    left: 7.5px;
    top: 3px;
    width: 1.5px;
    height: 6px;
    background: #36a9ff;
    transform-origin: bottom center;
    animation: tool-spin-slow 1.4s linear infinite;
  }
}

.icon-ask {
  .mark {
    position: absolute;
    inset: 0;
    color: #3866f5;
    font-size: var(--font-size-md);
    font-weight: 800;
    line-height: 1.15;
    text-align: center;
    animation: tool-bob 1s ease-in-out infinite;

    &::before {
      content: '?';
    }
  }
}

.icon-computer {
  animation: tool-bob 1.1s ease-in-out infinite;

  .screen {
    position: absolute;
    left: 1px;
    right: 1px;
    top: 1px;
    height: 10px;
    border: 1.5px solid #3866f5;
    border-radius: 2px;
  }

  .stand {
    position: absolute;
    left: 6px;
    right: 6px;
    bottom: 1px;
    height: 3px;
    background: #3866f5;
    border-radius: 1px;
  }
}

.icon-home {
  animation: tool-bob 1.1s ease-in-out infinite;

  .roof {
    position: absolute;
    top: 1px;
    left: 2px;
    width: 8px;
    height: 8px;
    border-top: 1.5px solid #3866f5;
    border-left: 1.5px solid #3866f5;
    transform: rotate(45deg);
  }

  .body {
    position: absolute;
    left: 3px;
    right: 3px;
    top: 7px;
    bottom: 1px;
    border: 1.5px solid #3866f5;
    border-top: 0;
  }
}

.icon-social {
  .bubble {
    position: absolute;
    inset: 1px 1px 3px 1px;
    border: 1.5px solid #3866f5;
    border-radius: 6px 6px 6px 2px;
    animation: tool-pulse 1.1s ease-in-out infinite;
  }
}

@keyframes tool-search-bob {
  0%,
  100% {
    transform: translate(0, 0) rotate(-8deg);
  }
  50% {
    transform: translate(1px, -1px) rotate(8deg);
  }
}

@keyframes tool-search-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgba(56, 102, 245, 0.35);
  }
  50% {
    box-shadow: 0 0 0 4px rgba(56, 102, 245, 0);
  }
}

@keyframes tool-scan {
  0% {
    top: 3px;
    opacity: 0.3;
  }
  50% {
    opacity: 1;
  }
  100% {
    top: 12px;
    opacity: 0.3;
  }
}

@keyframes tool-code-in {
  0%,
  100% {
    transform: rotate(135deg) translateX(0);
  }
  50% {
    transform: rotate(135deg) translateX(1px);
  }
}

@keyframes tool-blink {
  50% {
    opacity: 0;
  }
}

@keyframes tool-spin-slow {
  to {
    transform: rotate(360deg);
  }
}

@keyframes tool-bob {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-1.5px);
  }
}

@keyframes tool-look {
  0%,
  100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(2px);
  }
}

@keyframes tool-twinkle {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1.15);
  }
}

@keyframes tool-pulse {
  0%,
  100% {
    transform: scale(0.92);
    opacity: 0.75;
  }
  50% {
    transform: scale(1.08);
    opacity: 1;
  }
}

@keyframes tool-eq {
  0%,
  100% {
    height: 4px;
  }
  50% {
    height: 11px;
  }
}

@keyframes tool-tick {
  0%,
  40% {
    opacity: 0.2;
  }
  60%,
  100% {
    opacity: 1;
  }
}

@keyframes tool-node {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.35);
  }
}

@keyframes tool-link {
  0%,
  100% {
    opacity: 0.35;
    transform: scaleX(0.7);
  }
  50% {
    opacity: 1;
    transform: scaleX(1);
  }
}

@keyframes tool-done-pop {
  0% {
    transform: scale(0.4);
    opacity: 0;
  }
  70% {
    transform: scale(1.12);
    opacity: 1;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
