package com.guerram.DespensaFamily.repository;

import com.guerram.DespensaFamily.model.Compra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {


}
