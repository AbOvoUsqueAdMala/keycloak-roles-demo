package com.example.keycloakroles.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.keycloakroles.config.KeycloakProperties;
import com.example.keycloakroles.config.SecurityConfiguration;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class JwtValidationTest {

	private static final String ISSUER = "https://keycloak.example.test/realms/demo";
	private RSAKey signingKey;
	private JwtDecoder decoder;

	@BeforeEach
	void setUp() throws Exception {
		signingKey = new RSAKeyGenerator(2048).keyID("demo-key").generate();
		URI issuer = URI.create(ISSUER);
		URI certificates = URI.create(ISSUER + "/protocol/openid-connect/certs");
		RestTemplate restTemplate = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(manyTimes(), requestTo(certificates.toString()))
			.andRespond(withSuccess(new JWKSet(signingKey.toPublicJWK()).toString(),
				MediaType.APPLICATION_JSON));
		decoder = new SecurityConfiguration().jwtDecoder(restTemplate,
			new KeycloakProperties(issuer, certificates, issuer, "demo", "orders-api"));
	}

	@Test
	void acceptsCorrectSignatureIssuerAndAudience() {
		String token = token(signingKey, ISSUER, "orders-api", Instant.now().plusSeconds(120));

		assertThat(decoder.decode(token).getSubject()).isEqualTo("user-1");
	}

	@Test
	void rejectsWrongAudience() {
		String token = token(signingKey, ISSUER, "another-api", Instant.now().plusSeconds(120));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsWrongIssuer() {
		String token = token(signingKey, "https://untrusted.test", "orders-api",
			Instant.now().plusSeconds(120));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsExpiredTokenOutsideClockSkew() {
		String token = token(signingKey, ISSUER, "orders-api", Instant.now().minusSeconds(120));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsSignatureMadeWithAnotherPrivateKey() throws Exception {
		RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("demo-key").generate();
		String token = token(untrustedKey, ISSUER, "orders-api", Instant.now().plusSeconds(120));

		assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
	}

	private String token(RSAKey key, String issuer, String audience, Instant expiresAt) {
		NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
		JwtClaimsSet claims = JwtClaimsSet.builder().subject("user-1").issuer(issuer)
			.audience(List.of(audience)).issuedAt(Instant.now().minusSeconds(300))
			.expiresAt(expiresAt).build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId("demo-key").build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
