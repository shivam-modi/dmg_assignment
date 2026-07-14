package com.dmg.moviebooking;

import org.springframework.boot.SpringApplication;

public class TestMovieBookingSystemApplication {

	public static void main(String[] args) {
		SpringApplication.from(MovieBookingSystemApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
