
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


public interface GetAvatarCampaignsQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetAvatarCampaignsQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val avatarCampaigns: List<AvatarCampaignsItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class AvatarCampaignsItem(
  
    val id: String,
  
    val spaceId: String,
  
    val spaceTitle: String,
  
    val instagramHandle: String,
  
    val totalReelViews: Int,
  
    val linkClicks: Int,
  
    val inquiriesGenerated: Int,
  
    val generatedCaption: String,
  
    val storyOverlayTag: String,
  
    val lastNudgeText: String,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetAvatarCampaigns"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetAvatarCampaignsQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetAvatarCampaignsQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetAvatarCampaignsQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetAvatarCampaignsQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetAvatarCampaignsQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetAvatarCampaignsQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

