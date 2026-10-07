package com.evan.opensourcepulsebe.repository.mapper;

import com.evan.opensourcepulsebe.github.dto.GitHubRepoItem;
import com.evan.opensourcepulsebe.repository.GitHubRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface GitHubRepositoryMapper {

    @Mapping(target = "githubId", source = "id")
    @Mapping(target = "owner", source = "owner.login")
    @Mapping(target = "stars", source = "stargazersCount")
    GitHubRepository toEntity(GitHubRepoItem dto);
}
