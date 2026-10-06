#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform float uIntensity;
uniform float uTexelW;
uniform float uTexelH;
varying vec2 vTexSamplingCoord;

// §upscale : masque flou + renforcement de contour facon "anime upscale".
// Le seuil est bas (0.004) : sur une source 480p etiree, la difference DOIT
// se voir. uIntensity peut depasser 1.0 (reglage utilisateur jusqu'a 2.0).
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 c = texture2D(uTexSampler, uv).rgb;
  vec3 l = texture2D(uTexSampler, vec2(uv.x - uTexelW, uv.y)).rgb;
  vec3 r = texture2D(uTexSampler, vec2(uv.x + uTexelW, uv.y)).rgb;
  vec3 t = texture2D(uTexSampler, vec2(uv.x, uv.y - uTexelH)).rgb;
  vec3 b = texture2D(uTexSampler, vec2(uv.x, uv.y + uTexelH)).rgb;
  vec3 d1 = texture2D(uTexSampler, vec2(uv.x - uTexelW, uv.y - uTexelH)).rgb;
  vec3 d2 = texture2D(uTexSampler, vec2(uv.x + uTexelW, uv.y - uTexelH)).rgb;
  vec3 d3 = texture2D(uTexSampler, vec2(uv.x - uTexelW, uv.y + uTexelH)).rgb;
  vec3 d4 = texture2D(uTexSampler, vec2(uv.x + uTexelW, uv.y + uTexelH)).rgb;
  vec3 blur = (l + r + t + b) * 0.125 + (d1 + d2 + d3 + d4) * 0.0625 + c * 0.25;
  vec3 diff = c - blur;
  float amp = smoothstep(0.004, 0.045, length(diff));
  vec3 sharp = c + diff * uIntensity * (0.35 + 0.65 * amp);
  gl_FragColor = vec4(clamp(sharp, 0.0, 1.0), 1.0);
}
