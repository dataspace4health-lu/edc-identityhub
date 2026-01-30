rootProject.name = "edc-identityhub"

include("extensions")
include("extensions:superuser-seed")
include("extensions:user-seeding")
include("extensions:service-loader")
include("extensions:common")
include("extensions:common:http")
include("extensions:common:http:client")

include("services")

include("spi")
include("spi:manage-participant")

findProject(":extensions:user-seeding")?.name = "user-seeding"
findProject(":extensions:superuser-seed")?.name = "superuser-seed"
findProject(":extensions:service-loader")?.name = "service-loader"
findProject(":extensions:common:http:client")?.name = "client"
findProject(":spi:manage-participant")?.name = "manage-participant"

