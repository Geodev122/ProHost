
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


public interface GetAppUsersQuery :
    com.google.firebase.dataconnect.generated.GeneratedQuery<
      ProspaceConnectorConnector,
      GetAppUsersQuery.Data,
      Unit
    >
{
  

  
    @kotlinx.serialization.Serializable
  public data class Data(
  
    val appUsers: List<AppUsersItem>,
  
  ) {
    
      
        @kotlinx.serialization.Serializable
  public data class AppUsersItem(
  
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
    public val operationName: String = "GetAppUsers"

    public val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data> =
      kotlinx.serialization.serializer()

    public val variablesSerializer: kotlinx.serialization.SerializationStrategy<Unit> =
      kotlinx.serialization.serializer()
  }
}

public fun GetAppUsersQuery.ref(
  
): com.google.firebase.dataconnect.QueryRef<
    GetAppUsersQuery.Data,
    Unit
  > =
  ref(
    
      Unit
    
  )

public suspend fun GetAppUsersQuery.execute(

  

  ): com.google.firebase.dataconnect.QueryResult<
    GetAppUsersQuery.Data,
    Unit
  > =
  ref(
    
  ).execute()


  public fun GetAppUsersQuery.flow(
    
    ): kotlinx.coroutines.flow.Flow<GetAppUsersQuery.Data> =
    ref(
        
      ).subscribe()
      .flow
      ._flow_map { querySubscriptionResult -> querySubscriptionResult.result.getOrNull() }
      ._flow_filterNotNull()
      ._flow_map { it.data }

