package vn.cloud.llm_mcp_client_demo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;

import org.springframework.ai.mcp.client.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpSseClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({ McpSseClientProperties.class })
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.oauth2Client(Customizer.withDefaults())
				.csrf(CsrfConfigurer::disable)
				.build();
	}

	@Bean
	public List<NamedClientMcpTransport> webFluxClientTransports(McpSseClientProperties sseProperties,
			ObjectProvider<WebClient.Builder> webClientBuilderProvider,
			ObjectProvider<ObjectMapper> objectMapperProvider,
			OAuth2AuthorizedClientManager authorizedClientManager) {

		List<NamedClientMcpTransport> sseTransports = new ArrayList<>();

		var webClientBuilderTemplate = webClientBuilderProvider.getIfAvailable(WebClient::builder);
		var objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);

		for (Map.Entry<String, McpSseClientProperties.SseParameters> serverParameters : sseProperties.getConnections().entrySet()) {
			ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
					new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
			oauth2Client.setDefaultClientRegistrationId("my-client1");

			var webClientBuilder = webClientBuilderTemplate.clone().baseUrl(serverParameters.getValue().url());
			String sseEndpoint = serverParameters.getValue().sseEndpoint() != null
					? serverParameters.getValue().sseEndpoint() : "/sse";
			var transport = WebFluxSseClientTransport.builder(webClientBuilder.apply(oauth2Client.oauth2Configuration()))
					.sseEndpoint(sseEndpoint)
					.objectMapper(objectMapper)
					.build();
			sseTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
		}

		return sseTransports;
	}

	@Bean
	public OAuth2AuthorizedClientManager authorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AuthorizedClientService authorizedClientService) {

		OAuth2AuthorizedClientProvider authorizedClientProvider =
				OAuth2AuthorizedClientProviderBuilder.builder()
						.clientCredentials()
						.build();

		AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
				new AuthorizedClientServiceOAuth2AuthorizedClientManager(
						clientRegistrationRepository, authorizedClientService);
		authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

		return authorizedClientManager;
	}
}
