
@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "PropertyName",
  "MayBeConstant",
  "RedundantVisibilityModifier",
  "RedundantCompanionReference",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "unused",
)

package com.example.data.dataconnect


import kotlinx.coroutines.flow.filterNotNull as _flow_filterNotNull
import kotlinx.coroutines.flow.map as _flow_map


public interface GetSpaceListingsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetSpaceListingsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceListings: List<SpaceListingsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class SpaceListingsItem(
  
    val id: String,
  
    val title: String,
  
    val spaceType: String,
  
    val governorate: String,
  
    val district: String,
  
    val streetAddress: String,
  
    val floorInfo: String,
  
    val lat: Double,
  
    val lng: Double,
  
    val isShared: Boolean,
  
    val complementarySpecialties: List<String>,
  
    val residentPractitioners: List<String>,
  
    val essentialFacilities: List<String>,
  
    val equipmentJson: String,
  
    val rentalFormulasJson: String,
  
    val rulesJson: String,
  
    val scheduleJson: String,
  
    val ownerId: String,
  
    val ownerName: String,
  
    val ownerPhone: String,
  
    val ownerEmail: String,
  
    val isVerified: Boolean,
  
    val isActiveSubscription: Boolean,
  
    val subscriptionExpiryMillis: Long,
  
    val imageUrls: List<String>,
  
    val videoTourDurationSec: Int,
  
    val baseMonthlyRateUsd: Double,
  
    val avatarEngagementViews: Int,
  
    val avatarInquiryClicks: Int,
  
    val subdivisionsJson: String?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetSpaceListings"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetSpaceListingsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetSpaceListingsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetSpaceListingsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetSpaceListingsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetSpaceListingsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetSpaceListingsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

