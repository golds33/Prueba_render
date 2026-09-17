package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.demo.model.Producto;
import com.example.demo.service.ProductoService;

@Controller
@RequestMapping("/productos")
public class ProductoController {

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

	    @PostMapping("/guardar")
	    public String guardar(@ModelAttribute Producto producto){

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
