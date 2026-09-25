package com.guerram.DespensaFamily.service;

import com.guerram.DespensaFamily.config.NoEncontradoException;
import com.guerram.DespensaFamily.dto.CategoriaDTO;
import com.guerram.DespensaFamily.mapper.Mapper;
import com.guerram.DespensaFamily.model.Categoria;
import com.guerram.DespensaFamily.repository.CategoriaRepository;
import com.guerram.DespensaFamily.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    public CategoriaDTO crearCategoria(CategoriaDTO dto) {
        Categoria categoria = Mapper.toEntity(dto);
        Categoria guardada = categoriaRepository.save(categoria);
        return Mapper.toDTO(guardada);
    }

    public List<CategoriaDTO> listarCategorias() {
        return categoriaRepository.findAll()
                .stream()
                .map(Mapper::toDTO)
                .toList();
    }
    public CategoriaDTO actualizarCategoria(Long id, CategoriaDTO dto) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Categoria no encontrada"));

        categoria.setNombre(dto.getNombre());
        categoria.setDescripcion(dto.getDescripcion());

        return Mapper.toDTO(categoriaRepository.save(categoria));
    }

    public void eliminarCategoria(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Categoria no encontrada"));

        productoRepository.findAll().forEach(p -> {
            if (p.getCategoria() != null && p.getCategoria().getId().equals(id)) {
                p.setCategoria(null);
                productoRepository.save(p);
            }
        });

        categoriaRepository.delete(categoria);
    }
}
