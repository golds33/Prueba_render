package com.example.demo.controller;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.model.Producto;
import com.example.demo.service.ProductoService;

@Controller
@RequestMapping("/productos")
public class ProductoController {
	private static final Set<String> TIPOS_IMAGEN_PERMITIDOS = Set.of(
			MediaType.IMAGE_JPEG_VALUE,
			MediaType.IMAGE_PNG_VALUE,
			MediaType.IMAGE_GIF_VALUE,
			"image/webp");
	private static final long TAMANO_MAXIMO_IMAGEN = 5 * 1024 * 1024;

	 private ProductoService service;

	    public ProductoController(ProductoService service) {
	        this.service = service;
	    }

	    @GetMapping
	    public String listar(Model model){

	        model.addAttribute("productos", service.listar());

	        return "productos";
	    }

	    @GetMapping("/nuevo")
	    public String nuevo(Model model){

	        model.addAttribute("producto", new Producto());

	        return "form";
	    }

		@GetMapping("/editar/{id}")
		public String editar(@PathVariable Integer id, Model model){

			model.addAttribute("producto", obtenerProducto(id));

			return "form";
		}

		@GetMapping("/detalle/{id}")
		public String detalle(@PathVariable Integer id, Model model){

			model.addAttribute("producto", obtenerProducto(id));

			return "detalle";
		}

		@GetMapping("/imagen/{id}")
		public ResponseEntity<byte[]> imagen(@PathVariable Integer id) {
			Producto producto = obtenerProducto(id);
			if (producto.getImagenDatos() == null || producto.getImagenDatos().length == 0) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
			}

			MediaType mediaType = producto.getImagenContentType() == null
					? MediaType.APPLICATION_OCTET_STREAM
					: MediaType.parseMediaType(producto.getImagenContentType());
			return ResponseEntity.ok().contentType(mediaType).body(producto.getImagenDatos());
		}

	    @PostMapping("/guardar")
	    public String guardar(@ModelAttribute Producto producto,
				@RequestParam(name = "imagen", required = false) MultipartFile imagen){
			Producto productoExistente = producto.getId() == null ? null : obtenerProducto(producto.getId());

			if (imagen != null && !imagen.isEmpty()) {
				if (imagen.getSize() > TAMANO_MAXIMO_IMAGEN) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen no puede superar 5 MB");
				}
				String contentType = imagen.getContentType();
				if (contentType == null || !TIPOS_IMAGEN_PERMITIDOS.contains(contentType)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato de imagen no permitido");
				}
				try {
					producto.setImagenDatos(imagen.getBytes());
				} catch (IOException exception) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer la imagen", exception);
				}
				producto.setImagenContentType(contentType);
				producto.setImagenUrl(null);
			} else if (productoExistente != null) {
				producto.setImagenDatos(productoExistente.getImagenDatos());
				producto.setImagenContentType(productoExistente.getImagenContentType());
				producto.setImagenUrl(productoExistente.getImagenUrl());
			}

	        service.guardar(producto);

	        return "redirect:/productos";
	    }

		@PostMapping("/eliminar/{id}")
	    public String eliminar(@PathVariable Integer id){

	        service.eliminar(id);

	        return "redirect:/productos";
	    }

		private Producto obtenerProducto(Integer id){
			return service.buscarPorId(id)
					.orElseThrow(() -> new ResponseStatusException(
							HttpStatus.NOT_FOUND, "Producto no encontrado"));
		}
}
