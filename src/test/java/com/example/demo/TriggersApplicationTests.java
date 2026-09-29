package com.example.demo;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.repository.ProductoRepository;

@SpringBootTest
@AutoConfigureMockMvc
class TriggersApplicationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProductoRepository productoRepository;

	@Test
	void productWithStockAndImageCanBeCreatedAndDisplayed() throws Exception {
		mockMvc.perform(get("/productos/nuevo"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("id=\"imagen\" name=\"imagen\" type=\"file\"")))
			.andExpect(content().string(containsString("Arrastra una imagen aquí")));

		byte[] originalImage = { 1, 2, 3, 4 };
		MockMultipartFile initialImage = new MockMultipartFile(
				"imagen", "cafe.png", MediaType.IMAGE_PNG_VALUE, originalImage);

		mockMvc.perform(multipart("/productos/guardar")
				.param("nombre", "Cafe de prueba")
				.param("precio", "3.50")
				.param("stock", "12")
				.file(initialImage))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/productos"));

		Integer productId = productoRepository.findAll().get(0).getId();
		MockMultipartFile invalidImage = new MockMultipartFile(
				"imagen", "documento.txt", MediaType.TEXT_PLAIN_VALUE, originalImage);
		mockMvc.perform(multipart("/productos/guardar")
				.param("nombre", "Archivo no permitido")
				.param("precio", "1.00")
				.param("stock", "1")
				.file(invalidImage))
			.andExpect(status().isBadRequest());
		assertEquals(1, productoRepository.count());

		mockMvc.perform(get("/productos/editar/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Seleccionar imagen")))
			.andExpect(content().string(containsString("value=\"12\"")));

		mockMvc.perform(get("/productos"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("/productos/imagen/" + productId)))
			.andExpect(content().string(containsString("12 unidades")));

		mockMvc.perform(multipart("/productos/guardar")
				.param("id", productId.toString())
				.param("nombre", "Cafe de prueba")
				.param("precio", "3.50")
				.param("stock", "7"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/productos"));

		mockMvc.perform(get("/productos/detalle/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("Cafe de prueba")))
			.andExpect(content().string(containsString("/productos/imagen/" + productId)))
			.andExpect(content().string(containsString("Imagen de Cafe de prueba")))
			.andExpect(content().string(containsString("7 unidades")));

		mockMvc.perform(get("/productos/imagen/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(originalImage));

		byte[] replacementImage = { 5, 6, 7 };
		MockMultipartFile updatedImage = new MockMultipartFile(
				"imagen", "cafe.jpg", MediaType.IMAGE_JPEG_VALUE, replacementImage);
		mockMvc.perform(multipart("/productos/guardar")
				.param("id", productId.toString())
				.param("nombre", "Cafe de prueba")
				.param("precio", "3.50")
				.param("stock", "7")
				.file(updatedImage))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/productos/imagen/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
			.andExpect(content().bytes(replacementImage));

		mockMvc.perform(post("/productos/eliminar/{id}", productId))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/productos"));

		assertTrue(productoRepository.findById(productId).isEmpty());
	}

}
