#version 150

#moj_import <fog.glsl>
#moj_import <light.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out vec2 texCoord0;
out vec4 vertexColor;
out float vertexDistance;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    // Equivalent to GL_NORMALIZE after the original scaled projector matrix.
    vec3 normal = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, normal, Color);
    vertexDistance = fog_distance((ModelViewMat * vec4(Position, 1.0)).xyz, FogShape);
}
