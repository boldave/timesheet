package pl.dawid.timesheet.project;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
class ProjectController {

    private final ProjectRepository projects;

    ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    @GetMapping
    List<ProjectView> list() {
        return projects.findAll(Sort.by("id")).stream()
                .map(p -> new ProjectView(p.getId(), p.getName(), p.getColor()))
                .toList();
    }

    record ProjectView(Long id, String name, String color) {
    }
}
