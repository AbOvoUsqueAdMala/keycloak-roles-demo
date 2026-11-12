package com.example.keycloakroles;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KeycloakRolesApplication {

	public static void main(String[] arguments) {
		SpringApplication.run(KeycloakRolesApplication.class, arguments);
	}
}
