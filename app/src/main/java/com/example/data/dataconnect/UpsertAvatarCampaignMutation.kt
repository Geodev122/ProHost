
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



public interface UpsertAvatarCampaignMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertAvatarCampaignMutation.Data,
      UpsertAvatarCampaignMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
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
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val avatarCampaign_upsert: AvatarCampaignKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertAvatarCampaign"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertAvatarCampaignMutation.ref(
  
    id: String,spaceId: String,spaceTitle: String,instagramHandle: String,totalReelViews: Int,linkClicks: Int,inquiriesGenerated: Int,generatedCaption: String,storyOverlayTag: String,lastNudgeText: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertAvatarCampaignMutation.Data,
    UpsertAvatarCampaignMutation.Variables
  > =
  ref(
    
      UpsertAvatarCampaignMutation.Variables(
        id=id,spaceId=spaceId,spaceTitle=spaceTitle,instagramHandle=instagramHandle,totalReelViews=totalReelViews,linkClicks=linkClicks,inquiriesGenerated=inquiriesGenerated,generatedCaption=generatedCaption,storyOverlayTag=storyOverlayTag,lastNudgeText=lastNudgeText,
  
      )
    
  )

public suspend fun UpsertAvatarCampaignMutation.execute(

  
    
      id: String,spaceId: String,spaceTitle: String,instagramHandle: String,totalReelViews: Int,linkClicks: Int,inquiriesGenerated: Int,generatedCaption: String,storyOverlayTag: String,lastNudgeText: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertAvatarCampaignMutation.Data,
    UpsertAvatarCampaignMutation.Variables
  > =
  ref(
    
      id=id,spaceId=spaceId,spaceTitle=spaceTitle,instagramHandle=instagramHandle,totalReelViews=totalReelViews,linkClicks=linkClicks,inquiriesGenerated=inquiriesGenerated,generatedCaption=generatedCaption,storyOverlayTag=storyOverlayTag,lastNudgeText=lastNudgeText,
  
    
  ).execute()


