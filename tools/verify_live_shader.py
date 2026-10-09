"""Headless GLES smoke test, NOT an Android decoder test.
Run: EGL_PLATFORM=surfaceless PYOPENGL_PLATFORM=egl python tools/verify_live_shader.py
Requires PyOpenGL and a Mesa EGL/GLES implementation.
"""
import os, ctypes, pathlib
os.environ.setdefault('EGL_PLATFORM','surfaceless')
os.environ.setdefault('PYOPENGL_PLATFORM','egl')
from OpenGL.EGL import *
from OpenGL.GLES2 import *
root=pathlib.Path(__file__).resolve().parents[1]
display=eglGetDisplay(EGL_DEFAULT_DISPLAY);major=EGLint();minor=EGLint()
assert eglInitialize(display,major,minor)
attrs=(EGLint*13)(EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE)
config=EGLConfig();count=EGLint();assert eglChooseConfig(display,attrs,ctypes.byref(config),1,count) and count.value
surface=eglCreatePbufferSurface(display,config,(EGLint*5)(EGL_WIDTH,4,EGL_HEIGHT,4,EGL_NONE))
context=eglCreateContext(display,config,EGL_NO_CONTEXT,(EGLint*3)(EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE));assert eglMakeCurrent(display,surface,surface,context)
def compile(kind,path):
 shader=glCreateShader(kind);glShaderSource(shader,path.read_text());glCompileShader(shader)
 assert glGetShaderiv(shader,GL_COMPILE_STATUS),glGetShaderInfoLog(shader)
 return shader
program=glCreateProgram()
for kind,name in [(GL_VERTEX_SHADER,'vertex_es2.glsl'),(GL_FRAGMENT_SHADER,'live_video_es2.glsl')]:glAttachShader(program,compile(kind,root/'app/src/main/assets/shaders'/name))
glLinkProgram(program);assert glGetProgramiv(program,GL_LINK_STATUS),glGetProgramInfoLog(program)
glUseProgram(program)
coords=(ctypes.c_float*16)(-1,-1,0,1,1,-1,0,1,-1,1,0,1,1,1,0,1)
loc=glGetAttribLocation(program,'aFramePosition');glEnableVertexAttribArray(loc);glVertexAttribPointer(loc,4,GL_FLOAT,False,0,coords)
tex=GLuint();glGenTextures(1,ctypes.byref(tex));glBindTexture(GL_TEXTURE_2D,tex)
glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST)
glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
pixels=(ctypes.c_ubyte*64)(*([80,120,160,255]*16));glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,4,4,0,GL_RGBA,GL_UNSIGNED_BYTE,pixels)
glUniform1i(glGetUniformLocation(program,'uTexSampler'),0)
neutral={'uTexelW':.25,'uTexelH':.25,'uBrightness':0,'uSaturation':0,'uHue':0,'uContrast':1,'uGamma':1,'uTemperature':0,'uSharpness':0}
def render(changes):
 for k,v in dict(neutral,**changes).items():glUniform1f(glGetUniformLocation(program,k),v)
 glViewport(0,0,4,4);glDrawArrays(GL_TRIANGLE_STRIP,0,4)
 buf=(ctypes.c_ubyte*4)();glReadPixels(1,1,1,1,GL_RGBA,GL_UNSIGNED_BYTE,buf)
 assert glGetError()==GL_NO_ERROR
 return list(buf)
assert all(abs(a-b)<=1 for a,b in zip(render({}),[80,120,160,255]))
for i in range(500):
 changed=render({'uBrightness':.1,'uSharpness':float(i%3)/2,'uHue':.2,'uSaturation':.1})
 assert changed!=[80,120,160,255]
 assert all(abs(a-b)<=1 for a,b in zip(render({}),[80,120,160,255]))
print('PASS: shader compiled/linked; 1,000 uniform updates rendered with neutral restoration; no GL errors (Mesa GLES, not a phone).')
glDeleteProgram(program);eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroyContext(display,context);eglDestroySurface(display,surface);eglTerminate(display)
