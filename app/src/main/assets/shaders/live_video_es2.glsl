#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform float uTexelW, uTexelH, uBrightness, uSaturation, uHue;
uniform float uContrast, uGamma, uTemperature, uSharpness;
varying vec2 vTexSamplingCoord;
void main() {
    vec2 uv = vTexSamplingCoord;
    vec4 source = texture2D(uTexSampler, uv);
    vec3 c = source.rgb;
    if (uSharpness > 0.001) {
        vec3 neighbors = texture2D(uTexSampler, uv + vec2(uTexelW,0.0)).rgb
            + texture2D(uTexSampler, uv - vec2(uTexelW,0.0)).rgb
            + texture2D(uTexSampler, uv + vec2(0.0,uTexelH)).rgb
            + texture2D(uTexSampler, uv - vec2(0.0,uTexelH)).rgb;
        c += uSharpness * (c - neighbors * 0.25);
    }
    if (abs(uHue) > 0.0001) {
        vec3 axis = normalize(vec3(1.0));
        float co = cos(uHue), si = sin(uHue);
        c = c * co + cross(axis, c) * si + axis * dot(axis, c) * (1.0 - co);
    }
    if (abs(uSaturation) > 0.0001) {
        float gray = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c = mix(vec3(gray), c, 1.0 + uSaturation);
    }
    c = (c - 0.5) * uContrast + 0.5;
    c *= vec3(1.0 + 0.15 * uTemperature, 1.0, 1.0 - 0.15 * uTemperature);
    if (abs(uGamma - 1.0) > 0.0001) c = pow(max(c, vec3(0.0)), vec3(1.0 / uGamma));
    c += uBrightness;
    gl_FragColor = vec4(max(c, vec3(0.0)), source.a);
}
