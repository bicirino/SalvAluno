package main.java.com.salvAluno.repository;

import com.salvAluno.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository; 
import org.springframework.stereotype.Repository; 

@Repository 
public interface TaskRepository extends JpaRepository<Task, Long>{ 

    
}