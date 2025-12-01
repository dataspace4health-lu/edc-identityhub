rootProject.name = "edc-identityhub"
include("extensions")
include("services")
include("spi")

include("extensions:user-seeding")
include("extensions:service-loader")

include("spi:manage-participant")

findProject(":extensions:user-seeding")?.name = "user-seeding"
findProject(":extensions:service-loader")?.name = "service-loader"
findProject(":spi:manage-participant")?.name = "manage-participant"
