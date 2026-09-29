package com.fmatrestaurant.menu;

import org.springframework.boot.SpringApplication;

public class TestMenuBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(MenuBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
