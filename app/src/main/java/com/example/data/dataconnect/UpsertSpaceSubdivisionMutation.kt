
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



public interface UpsertSpaceSubdivisionMutation :
    com.google.firebase.dataconnect.generated.GeneratedMutation<
      ProspaceConnectorConnector,
      UpsertSpaceSubdivisionMutation.Data,
      UpsertSpaceSubdivisionMutation.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
    val spaceId: String,
  
    val name: String,
  
    val type: String,
  
    val amenities: List<String>,
  
    val strategiesJson: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val spaceSubdivision_upsert: SpaceSubdivisionKey,
  
  ) {
    
    
  }
  

  public companion object {
    public val operationName: String = "UpsertSpaceSubdivision"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun UpsertSpaceSubdivisionMutation.ref(
  
    id: String,spaceId: String,name: String,type: String,amenities: List<String>,strategiesJson: String,

  
  
): com.google.firebase.dataconnect.MutationRef<
    UpsertSpaceSubdivisionMutation.Data,
    UpsertSpaceSubdivisionMutation.Variables
  > =
  ref(
    
      UpsertSpaceSubdivisionMutation.Variables(
        id=id,spaceId=spaceId,name=name,type=type,amenities=amenities,strategiesJson=strategiesJson,
  
      )
    
  )

public suspend fun UpsertSpaceSubdivisionMutation.execute(

  
    
      id: String,spaceId: String,name: String,type: String,amenities: List<String>,strategiesJson: String,

  

  ): com.google.firebase.dataconnect.MutationResult<
    UpsertSpaceSubdivisionMutation.Data,
    UpsertSpaceSubdivisionMutation.Variables
  > =
  ref(
    
      id=id,spaceId=spaceId,name=name,type=type,amenities=amenities,strategiesJson=strategiesJson,
  
    
  ).execute()


