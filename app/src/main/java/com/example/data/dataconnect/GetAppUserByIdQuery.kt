
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


public interface GetAppUserByIdQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetAppUserByIdQuery.Data,
      GetAppUserByIdQuery.Variables
    >
{
  
    @kotlinx.serialization.Serializable
  public data class Variables(
  
    val id: String,
  
  ) {
    
    
  }
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val appUser: AppUser?,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class AppUser(
  
    val id: String,
  
    val email: String,
  
    val fullName: String,
  
    val role: String,
  
    val specialty: String,
  
    val phone: String,
  
    val affiliation: String,
  
    val syndicateNumber: String,
  
    val governorate: String,
  
    val isVerified: Boolean,
  
    val subscriptionExpiryMillis: Long?,
  
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    public val operationName: String = "GetAppUserById"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables> =
      kotlinx.serialization.serializer()
  }
}

public fun GetAppUserByIdQuery.ref(
  
    id: String,

  
  
): com.google.firebase.dataconnect.QueryRef<
    GetAppUserByIdQuery.Data,
    GetAppUserByIdQuery.Variables
  > =
  ref(
    
      GetAppUserByIdQuery.Variables(
        id=id,
  
      )
    
  )

public suspend fun GetAppUserByIdQuery.execute(

  
    
      id: String,

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetAppUserByIdQuery.Data,
    GetAppUserByIdQuery.Variables
  > =
  ref(
    
      id=id,
  
    
  ).execute()


  public fun GetAppUserByIdQuery.flow(
    
      id: String,

  
    
    ): kotlinx.coroutines.flow.Flow<GetAppUserByIdQuery.Data> =
    ref(
        
          id=id,
  
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

