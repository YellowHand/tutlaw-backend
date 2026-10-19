package pl.tutlaw.backend.course;

import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tutlaw.backend.common.GroupCount;
import pl.tutlaw.backend.course.dto.CourseResponse;
import pl.tutlaw.backend.pool.Pool;
import pl.tutlaw.backend.question.QuestionRepository;
import pl.tutlaw.backend.source.QuestionSourceRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Przedmioty studenta. To tylko podział aktów: wybór przedmiotów zawęża pulę (aplikacja zamienia go na wybór aktów
 * przez {@code PUT /api/me/pool/acts}), a pytania i tak należą do aktów.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseRepository courseRepository;
    private final QuestionRepository questionRepository;
    private final QuestionSourceRepository sourceRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public List<CourseResponse> list() {
        Map<Long, List<Long>> actsByCourse = new HashMap<>();
        Map<Long, Long> perAct = new HashMap<>();
        List<CourseRepository.CourseAct> pairs = courseRepository.findCourseActs();
        List<Long> actIds = pairs.stream().map(CourseRepository.CourseAct::getActId).distinct().toList();
        for (CourseRepository.CourseAct pair : pairs) {
            actsByCourse.computeIfAbsent(pair.getCourseId(), k -> new ArrayList<>()).add(pair.getActId());
        }
        if (!actIds.isEmpty()) {
            List<Long> allSources = sourceRepository.findAll().stream().map(s -> s.getId()).toList();
            for (GroupCount row : questionRepository.countByAct(actIds, allSources.isEmpty() ? List.of(Pool.NO_SUCH_ID) : allSources)) {
                perAct.put(row.getGroupId(), row.getTotal());
            }
        }

        List<CourseResponse> result = new ArrayList<>();
        for (Course course : courseRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<Long> acts = actsByCourse.getOrDefault(course.getId(), List.of());
            int questions = acts.stream().mapToInt(id -> perAct.getOrDefault(id, 0L).intValue()).sum();
            result.add(new CourseResponse(course.getId(), course.getName(), course.getSlug(), questions, acts));
        }
        return result;
    }
}
