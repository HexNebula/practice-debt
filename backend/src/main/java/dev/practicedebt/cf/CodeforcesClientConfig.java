package dev.practicedebt.cf;

import dev.practicedebt.config.CodeforcesProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CodeforcesClientConfig {

    @Bean
    RequestPacer codeforcesPacer(CodeforcesProperties props) {
        return new RequestPacer(props.minRequestInterval());
    }

    @Bean
    RestClient codeforcesRestClient(RestClient.Builder builder, CodeforcesProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.connectTimeout())
                .withReadTimeout(props.readTimeout());

        // No Accept-Encoding header on purpose: the detected request factory does not decode
        // gzip, so asking for it would hand Jackson a compressed stream. Uncompressed it is.
        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .defaultHeader("User-Agent", "practice-debt/0.1 (personal practice tracker)")
                .build();
    }
}
