package ir.linuxian.second.config;

import ir.linuxian.second.service.MediaService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MediaService mediaService;

    public WebConfig(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + mediaService.getRoot() + "/");
    }
}
