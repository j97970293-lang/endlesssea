#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform float uTexelW;
uniform float uTexelH;
varying vec2 vTexSamplingCoord;

// Masque flou (unsharp mask) léger, façon « anime upscale » :
// on ré-injecte la différence entre l'image et sa version adoucie.
// 5 échantillons seulement -> coût GPU négligeable sur mobile.
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec3 l = texture2D(uTexSampler, vec2(uv.x - uTexelW, uv.y)).rgb;
  vec3 r = texture2D(uTexSampler, vec2(uv.x + uTexelW, uv.y)).rgb;
  vec3 t = texture2D(uTexSampler, vec2(uv.x, uv.y - uTexelH)).rgb;
  vec3 b = texture2D(uTexSampler, vec2(uv.x, uv.y + uTexelH)).rgb;
  vec3 blur = (l + r + t + b) * 0.25;
  vec3 sharp = c + (c - blur) * uIntensity;
  gl_FragColor = vec4(clamp(sharp, 0.0, 1.0), 1.0);
}
