package com.evan.opensourcepulsebe.issue.mapper;

import com.evan.opensourcepulsebe.github.dto.GitHubIssueItem;
import com.evan.opensourcepulsebe.github.dto.GitHubLabelItem;
import com.evan.opensourcepulsebe.issue.Issue;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = {Instant.class}
)
public interface IssueMapper {

    @Mapping(target = "id", ignore = true) // DB generated
    @Mapping(target = "githubId", source = "dto.id")
    @Mapping(target = "repository", source = "repository")
    @Mapping(target = "number", source = "dto.number")
    @Mapping(target = "htmlUrl", source = "dto.htmlUrl")
    @Mapping(target = "state", source = "dto.state")
    @Mapping(target = "commentsCount", source = "dto.comments", defaultValue = "0")
    @Mapping(target = "title", source = "dto.title", qualifiedByName = "truncate500")
    @Mapping(target = "bodySnippet", source = "dto.body", qualifiedByName = "truncate5000")
    @Mapping(target = "githubCreatedAt", source = "dto.createdAt", defaultExpression = "java(Instant.now())")
    @Mapping(target = "githubUpdatedAt", source = "dto.updatedAt", defaultExpression = "java(Instant.now())")
    @Mapping(target = "labels", source = "dto.labels", qualifiedByName = "mapLabels")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Issue toEntity(GitHubIssueItem dto, GitHubRepository repository);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "githubId", ignore = true)
    @Mapping(target = "repository", ignore = true)
    @Mapping(target = "number", ignore = true) // shouldn't change
    @Mapping(target = "htmlUrl", ignore = true) // shouldn't change
    @Mapping(target = "githubCreatedAt", ignore = true) // shouldn't change
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "title", source = "title", qualifiedByName = "truncate500")
    @Mapping(target = "bodySnippet", source = "body", qualifiedByName = "truncate5000")
    @Mapping(target = "commentsCount", source = "comments", defaultValue = "0")
    @Mapping(target = "githubUpdatedAt", source = "updatedAt", defaultExpression = "java(Instant.now())")
    @Mapping(target = "labels", source = "labels", qualifiedByName = "mapLabels")
    void updateEntityFromDto(GitHubIssueItem dto, @MappingTarget Issue entity);

    @Named("truncate500")
    default String truncate500(String text) {
        if (text == null) return null;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    @Named("truncate5000")
    default String truncate5000(String text) {
        if (text == null) return null;
        return text.length() > 5000 ? text.substring(0, 5000) : text;
    }

    // Must return a mutable list: on update MapStruct calls clear()/addAll() on the entity's existing list.
    @Named("mapLabels")
    default List<String> mapLabels(List<GitHubLabelItem> labels) {
        if (labels == null) return new ArrayList<>();
        return labels.stream()
                .map(GitHubLabelItem::name)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
