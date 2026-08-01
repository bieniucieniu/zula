package com.zula.features.feed

class TraitSeeder(
    private val repository: FeedRepository,
) {
    fun seedIfEmpty() {
        if (repository.countTraits() > 0L) return
        repository.transaction {
            val goods = insertTrait(null, "goods", "Goods", 0)
            val services = insertTrait(null, "services", "Services", 1)
            val travel = insertTrait(null, "travel", "Travel", 2)
            insertTrait(goods.id, "electronics", "Electronics", 0)
            insertTrait(goods.id, "furniture", "Furniture", 1)
            insertTrait(services.id, "repair", "Repair", 0)
            insertTrait(services.id, "tutoring", "Tutoring", 1)
            insertTrait(travel.id, "rideshare", "Rideshare", 0)
            insertTrait(travel.id, "courier", "Courier", 1)
        }
    }
}
