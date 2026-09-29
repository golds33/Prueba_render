package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.repository.ProductoRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TriggersApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductoRepository productoRepository;

	@Test
	void productWithStockAndImageCanBeCreatedAndDisplayed() throws Exception {
		mockMvc.perform(post("/productos/guardar")
				.param("nombre", "Cafe de prueba")
				.param("precio", "3.50")
				.param("stock", "12")
				.param("imagenUrl", "https://example.com/cafe.jpg"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/productos"));

		Integer productId = productoRepository.findAll().get(0).getId();

		mockMvc.perform(get("/productos/editar/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("value=\"https://example.com/cafe.jpg\"")))
			.andExpect(content().string(containsString("value=\"12\"")));

		mockMvc.perform(get("/productos"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("https://example.com/cafe.jpg")))
			.andExpect(content().string(containsString("12 unidades")));

		mockMvc.perform(post("/productos/guardar")
				.param("id", productId.toString())
				.param("nombre", "Cafe de prueba")
				.param("precio", "3.50")
				.param("stock", "7")
				.param("imagenUrl", "https://example.com/cafe-actualizado.jpg"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/productos"));

		mockMvc.perform(get("/productos/detalle/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Cafe de prueba")))
			.andExpect(content().string(containsString("https://example.com/cafe-actualizado.jpg")))
			.andExpect(content().string(containsString("Imagen de Cafe de prueba")))
			.andExpect(content().string(containsString("7 unidades")));
	}

}
