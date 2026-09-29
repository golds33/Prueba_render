    package com.example.demo.model;

    import jakarta.persistence.Column;
    import jakarta.persistence.Entity;
    import jakarta.persistence.GeneratedValue;
    import jakarta.persistence.GenerationType;
    import jakarta.persistence.Id;
    import jakarta.persistence.Table;

    @Entity
    @Table(name="productos")
    public class Producto {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Integer id;

        private String nombre;
        private double precio;
        private Integer stock = 0;

        @Column(length = 2048)
        private String imagenUrl;

        private byte[] imagenDatos;

        @Column(length = 100)
        private String imagenContentType;

        public Producto() {
        }

        public Producto(Integer id, String nombre, double precio) {
            this.id = id;
            this.nombre = nombre;
            this.precio = precio;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }

        public String getNombre() {
            return nombre;
        }

        public void setNombre(String nombre) {
            this.nombre = nombre;
        }

        public double getPrecio() {
            return precio;
        }

        public void setPrecio(double precio) {
            this.precio = precio;
        }

        public Integer getStock() {
            return stock == null ? 0 : stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock == null ? 0 : stock;
        }

        public String getImagenUrl() {
            return imagenUrl;
        }

        public void setImagenUrl(String imagenUrl) {
            this.imagenUrl = imagenUrl == null || imagenUrl.isBlank() ? null : imagenUrl.trim();
        }

        public byte[] getImagenDatos() {
            return imagenDatos;
        }

        public void setImagenDatos(byte[] imagenDatos) {
            this.imagenDatos = imagenDatos;
        }

        public String getImagenContentType() {
            return imagenContentType;
        }

        public void setImagenContentType(String imagenContentType) {
            this.imagenContentType = imagenContentType;
        }
    }
