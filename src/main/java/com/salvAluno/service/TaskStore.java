package com.salvAluno.service;

import com.salvAluno.domain.Task;
import com.salvAluno.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskStore {

	private final TaskRepository taskRepository;

	public TaskStore(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	@Transactional
	public void substituirDoAluno(String ra, List<Task> tarefas) {
		taskRepository.deleteByOwnerRa(ra);
		if (!tarefas.isEmpty()) {
			taskRepository.saveAll(tarefas);
		}
	}
}
