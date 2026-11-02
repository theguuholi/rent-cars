package com.github.theguuholi.rentcars;

import org.springframework.boot.SpringApplication;

public class TestRentCarsApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(RentCarsApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
