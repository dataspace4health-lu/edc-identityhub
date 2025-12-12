rootProject.name = "edc-identityhub"
include("extensions")
include("extensions:superuser-seed")
findProject(":extensions:superuser-seed")?.name = "superuser-seed"
