#version 330

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
#endif

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
in ivec2 UV2;
#endif

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
uniform sampler2D Sampler2;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
#endif

out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

#if !defined(IS_GUI) && !defined(IS_SEE_THROUGH)
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color * sample_lightmap(Sampler2, UV2);
#else
    vertexColor = Color;
#ifdef IS_GUI
    // HotbarX: vanilla container labels are exactly #404040; draw them crimson instead.
    if (all(lessThan(abs(Color.rgb - vec3(64.0 / 255.0)), vec3(0.5 / 255.0)))) {
        vertexColor = vec4(1.0, 0.0, 0.0, Color.a);
    }
    // HotbarX: the XP level and affordable anvil/enchanting costs are exactly #80FF20; draw them pale crimson (#FFE5E5).
    if (all(lessThan(abs(Color.rgb - vec3(128.0, 255.0, 32.0) / 255.0), vec3(0.5 / 255.0)))) {
        vertexColor = vec4(1.0, 229.0 / 255.0, 229.0 / 255.0, Color.a);
    }
#endif
#endif
    texCoord0 = UV0;
}
