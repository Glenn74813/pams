package com.avtech.pams.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the built React app (frontend-react/dist) from the same process when pams.frontend-dir
 * is set, so a server needs one service and one certificate. Paths that are not a file and not
 * under /api get index.html, where the React router takes over.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendDir;

    public WebConfig(@Value("${pams.frontend-dir:}") String frontendDir) {
        this.frontendDir = frontendDir;
    }

    /** The resource handler skips the empty path, so "/" is forwarded to index.html explicitly. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (frontendDir == null || frontendDir.isBlank()) return;
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (frontendDir == null || frontendDir.isBlank()) return;
        Path root = Path.of(frontendDir).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("index.html"))) {
            throw new IllegalStateException("pams.frontend-dir " + root + " has no index.html; build the frontend first (npm run build).");
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(new FileSystemResource(root.toString() + "/"))
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        if (path.startsWith("api/")) return null;
                        Resource file = location.createRelative(path);
                        if (file.exists() && file.isReadable()) return file;
                        return location.createRelative("index.html");
                    }
                });
    }
}
