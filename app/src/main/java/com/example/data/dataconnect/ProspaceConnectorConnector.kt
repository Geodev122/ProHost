
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

import com.google.firebase.dataconnect.getInstance as _fdcGetInstance
import kotlin.time.Duration.Companion.milliseconds as _milliseconds

public interface ProspaceConnectorConnector : com.google.firebase.dataconnect.generated.GeneratedConnector<ProspaceConnectorConnector> {
  override val dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect

  
    public val deleteBookingRequest: DeleteBookingRequestMutation
  
    public val deleteSpaceListing: DeleteSpaceListingMutation
  
    public val deleteSpaceSubdivision: DeleteSpaceSubdivisionMutation
  
    public val getAdminPricingStates: GetAdminPricingStatesQuery
  
    public val getAppUserById: GetAppUserByIdQuery
  
    public val getAppUsers: GetAppUsersQuery
  
    public val getAuditSecurityLogs: GetAuditSecurityLogsQuery
  
    public val getAvatarCampaigns: GetAvatarCampaignsQuery
  
    public val getBookingRequests: GetBookingRequestsQuery
  
    public val getSpaceListings: GetSpaceListingsQuery
  
    public val getSpaceSubdivisions: GetSpaceSubdivisionsQuery
  
    public val getSpaceSubdivisionsBySpaceId: GetSpaceSubdivisionsBySpaceIdQuery
  
    public val getSuccessfulTransactionsInRange: GetSuccessfulTransactionsInRangeQuery
  
    public val getWhishTransactions: GetWhishTransactionsQuery
  
    public val insertAuditSecurityLog: InsertAuditSecurityLogMutation
  
    public val insertWhishTransaction: InsertWhishTransactionMutation
  
    public val upsertAdminPricingState: UpsertAdminPricingStateMutation
  
    public val upsertAppUser: UpsertAppUserMutation
  
    public val upsertAvatarCampaign: UpsertAvatarCampaignMutation
  
    public val upsertBookingRequest: UpsertBookingRequestMutation
  
    public val upsertSpaceListing: UpsertSpaceListingMutation
  
    public val upsertSpaceSubdivision: UpsertSpaceSubdivisionMutation
  
    public val upsertWhishTransaction: UpsertWhishTransactionMutation
  

  public companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    public val config: com.google.firebase.dataconnect.ConnectorConfig = com.google.firebase.dataconnect.ConnectorConfig(
      connector = "prospace-connector",
      location = "europe-west1",
      serviceId = "prospace-dataconnect-medlb",
    )

    public fun getInstance(
      dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect
    ):ProspaceConnectorConnector = synchronized(instances) {
      instances.getOrPut(dataConnect) {
        ProspaceConnectorConnectorImpl(dataConnect)
      }
    }

    private val instances = java.util.WeakHashMap<com.google.firebase.dataconnect.FirebaseDataConnect, ProspaceConnectorConnectorImpl>()

    
  }
}

public val ProspaceConnectorConnector.Companion.instance:ProspaceConnectorConnector
  get() = getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(
    config
  ))

public fun ProspaceConnectorConnector.Companion.getInstance(
  settings: com.google.firebase.dataconnect.DataConnectSettings = com.google.firebase.dataconnect.DataConnectSettings()
):ProspaceConnectorConnector =
  getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(config, settings))

public fun ProspaceConnectorConnector.Companion.getInstance(
  app: com.google.firebase.FirebaseApp,
  settings: com.google.firebase.dataconnect.DataConnectSettings = com.google.firebase.dataconnect.DataConnectSettings()
):ProspaceConnectorConnector =
  getInstance(com.google.firebase.dataconnect.FirebaseDataConnect._fdcGetInstance(app, config, settings))

private class ProspaceConnectorConnectorImpl(
  override val dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect
) : ProspaceConnectorConnector {
  
    override val deleteBookingRequest by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteBookingRequestMutationImpl(this)
    }
  
    override val deleteSpaceListing by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteSpaceListingMutationImpl(this)
    }
  
    override val deleteSpaceSubdivision by lazy(LazyThreadSafetyMode.PUBLICATION) {
      DeleteSpaceSubdivisionMutationImpl(this)
    }
  
    override val getAdminPricingStates by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetAdminPricingStatesQueryImpl(this)
    }
  
    override val getAppUserById by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetAppUserByIdQueryImpl(this)
    }
  
    override val getAppUsers by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetAppUsersQueryImpl(this)
    }
  
    override val getAuditSecurityLogs by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetAuditSecurityLogsQueryImpl(this)
    }
  
    override val getAvatarCampaigns by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetAvatarCampaignsQueryImpl(this)
    }
  
    override val getBookingRequests by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetBookingRequestsQueryImpl(this)
    }
  
    override val getSpaceListings by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetSpaceListingsQueryImpl(this)
    }
  
    override val getSpaceSubdivisions by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetSpaceSubdivisionsQueryImpl(this)
    }
  
    override val getSpaceSubdivisionsBySpaceId by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetSpaceSubdivisionsBySpaceIdQueryImpl(this)
    }
  
    override val getSuccessfulTransactionsInRange by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetSuccessfulTransactionsInRangeQueryImpl(this)
    }
  
    override val getWhishTransactions by lazy(LazyThreadSafetyMode.PUBLICATION) {
      GetWhishTransactionsQueryImpl(this)
    }
  
    override val insertAuditSecurityLog by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertAuditSecurityLogMutationImpl(this)
    }
  
    override val insertWhishTransaction by lazy(LazyThreadSafetyMode.PUBLICATION) {
      InsertWhishTransactionMutationImpl(this)
    }
  
    override val upsertAdminPricingState by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertAdminPricingStateMutationImpl(this)
    }
  
    override val upsertAppUser by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertAppUserMutationImpl(this)
    }
  
    override val upsertAvatarCampaign by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertAvatarCampaignMutationImpl(this)
    }
  
    override val upsertBookingRequest by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertBookingRequestMutationImpl(this)
    }
  
    override val upsertSpaceListing by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertSpaceListingMutationImpl(this)
    }
  
    override val upsertSpaceSubdivision by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertSpaceSubdivisionMutationImpl(this)
    }
  
    override val upsertWhishTransaction by lazy(LazyThreadSafetyMode.PUBLICATION) {
      UpsertWhishTransactionMutationImpl(this)
    }
  

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun operations(): List<com.google.firebase.dataconnect.generated.GeneratedOperation<ProspaceConnectorConnector, *, *>> =
    queries() + mutations()

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun mutations(): List<com.google.firebase.dataconnect.generated.GeneratedMutation<ProspaceConnectorConnector, *, *>> =
    listOf(
      deleteBookingRequest,
        deleteSpaceListing,
        deleteSpaceSubdivision,
        insertAuditSecurityLog,
        insertWhishTransaction,
        upsertAdminPricingState,
        upsertAppUser,
        upsertAvatarCampaign,
        upsertBookingRequest,
        upsertSpaceListing,
        upsertSpaceSubdivision,
        upsertWhishTransaction,
        
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun queries(): List<com.google.firebase.dataconnect.generated.GeneratedQuery<ProspaceConnectorConnector, *, *>> =
    listOf(
      getAdminPricingStates,
        getAppUserById,
        getAppUsers,
        getAuditSecurityLogs,
        getAvatarCampaigns,
        getBookingRequests,
        getSpaceListings,
        getSpaceSubdivisions,
        getSpaceSubdivisionsBySpaceId,
        getSuccessfulTransactionsInRange,
        getWhishTransactions,
        
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(dataConnect: com.google.firebase.dataconnect.FirebaseDataConnect) =
    ProspaceConnectorConnectorImpl(dataConnect)

  override fun equals(other: Any?): Boolean =
    other is ProspaceConnectorConnectorImpl &&
    other.dataConnect == dataConnect

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "ProspaceConnectorConnectorImpl",
      dataConnect,
    )

  override fun toString(): String =
    "ProspaceConnectorConnectorImpl(dataConnect=$dataConnect)"
}



private open class ProspaceConnectorConnectorGeneratedQueryImpl<Data, Variables>(
  override val connector: ProspaceConnectorConnector,
  override val operationName: String,
  override val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
  override val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
) : com.google.firebase.dataconnect.generated.GeneratedQuery<ProspaceConnectorConnector, Data, Variables> {

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(
    connector: ProspaceConnectorConnector,
    operationName: String,
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
    variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
  ) =
    ProspaceConnectorConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variablesSerializer: kotlinx.serialization.SerializationStrategy<NewVariables>
  ) =
    ProspaceConnectorConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<NewData>
  ) =
    ProspaceConnectorConnectorGeneratedQueryImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun equals(other: Any?): Boolean =
    other is ProspaceConnectorConnectorGeneratedQueryImpl<*,*> &&
    other.connector == connector &&
    other.operationName == operationName &&
    other.dataDeserializer == dataDeserializer &&
    other.variablesSerializer == variablesSerializer

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "ProspaceConnectorConnectorGeneratedQueryImpl",
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun toString(): String =
    "ProspaceConnectorConnectorGeneratedQueryImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}

private open class ProspaceConnectorConnectorGeneratedMutationImpl<Data, Variables>(
  override val connector: ProspaceConnectorConnector,
  override val operationName: String,
  override val dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
  override val variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
) : com.google.firebase.dataconnect.generated.GeneratedMutation<ProspaceConnectorConnector, Data, Variables> {

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun copy(
    connector: ProspaceConnectorConnector,
    operationName: String,
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<Data>,
    variablesSerializer: kotlinx.serialization.SerializationStrategy<Variables>,
  ) =
    ProspaceConnectorConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewVariables> withVariablesSerializer(
    variablesSerializer: kotlinx.serialization.SerializationStrategy<NewVariables>
  ) =
    ProspaceConnectorConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  @com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
  override fun <NewData> withDataDeserializer(
    dataDeserializer: kotlinx.serialization.DeserializationStrategy<NewData>
  ) =
    ProspaceConnectorConnectorGeneratedMutationImpl(
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun equals(other: Any?): Boolean =
    other is ProspaceConnectorConnectorGeneratedMutationImpl<*,*> &&
    other.connector == connector &&
    other.operationName == operationName &&
    other.dataDeserializer == dataDeserializer &&
    other.variablesSerializer == variablesSerializer

  override fun hashCode(): Int =
    java.util.Objects.hash(
      "ProspaceConnectorConnectorGeneratedMutationImpl",
      connector, operationName, dataDeserializer, variablesSerializer
    )

  override fun toString(): String =
    "ProspaceConnectorConnectorGeneratedMutationImpl(" +
    "operationName=$operationName, " +
    "dataDeserializer=$dataDeserializer, " +
    "variablesSerializer=$variablesSerializer, " +
    "connector=$connector)"
}



private class DeleteBookingRequestMutationImpl(
  connector: ProspaceConnectorConnector
):
  DeleteBookingRequestMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      DeleteBookingRequestMutation.Data,
      DeleteBookingRequestMutation.Variables
  >(
    connector,
    DeleteBookingRequestMutation.Companion.operationName,
    DeleteBookingRequestMutation.Companion.dataDeserializer,
    DeleteBookingRequestMutation.Companion.variablesSerializer,
  )


private class DeleteSpaceListingMutationImpl(
  connector: ProspaceConnectorConnector
):
  DeleteSpaceListingMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      DeleteSpaceListingMutation.Data,
      DeleteSpaceListingMutation.Variables
  >(
    connector,
    DeleteSpaceListingMutation.Companion.operationName,
    DeleteSpaceListingMutation.Companion.dataDeserializer,
    DeleteSpaceListingMutation.Companion.variablesSerializer,
  )


private class DeleteSpaceSubdivisionMutationImpl(
  connector: ProspaceConnectorConnector
):
  DeleteSpaceSubdivisionMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      DeleteSpaceSubdivisionMutation.Data,
      DeleteSpaceSubdivisionMutation.Variables
  >(
    connector,
    DeleteSpaceSubdivisionMutation.Companion.operationName,
    DeleteSpaceSubdivisionMutation.Companion.dataDeserializer,
    DeleteSpaceSubdivisionMutation.Companion.variablesSerializer,
  )


private class GetAdminPricingStatesQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetAdminPricingStatesQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetAdminPricingStatesQuery.Data,
      Unit
  >(
    connector,
    GetAdminPricingStatesQuery.Companion.operationName,
    GetAdminPricingStatesQuery.Companion.dataDeserializer,
    GetAdminPricingStatesQuery.Companion.variablesSerializer,
  )


private class GetAppUserByIdQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetAppUserByIdQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetAppUserByIdQuery.Data,
      GetAppUserByIdQuery.Variables
  >(
    connector,
    GetAppUserByIdQuery.Companion.operationName,
    GetAppUserByIdQuery.Companion.dataDeserializer,
    GetAppUserByIdQuery.Companion.variablesSerializer,
  )


private class GetAppUsersQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetAppUsersQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetAppUsersQuery.Data,
      Unit
  >(
    connector,
    GetAppUsersQuery.Companion.operationName,
    GetAppUsersQuery.Companion.dataDeserializer,
    GetAppUsersQuery.Companion.variablesSerializer,
  )


private class GetAuditSecurityLogsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetAuditSecurityLogsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetAuditSecurityLogsQuery.Data,
      Unit
  >(
    connector,
    GetAuditSecurityLogsQuery.Companion.operationName,
    GetAuditSecurityLogsQuery.Companion.dataDeserializer,
    GetAuditSecurityLogsQuery.Companion.variablesSerializer,
  )


private class GetAvatarCampaignsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetAvatarCampaignsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetAvatarCampaignsQuery.Data,
      Unit
  >(
    connector,
    GetAvatarCampaignsQuery.Companion.operationName,
    GetAvatarCampaignsQuery.Companion.dataDeserializer,
    GetAvatarCampaignsQuery.Companion.variablesSerializer,
  )


private class GetBookingRequestsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetBookingRequestsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetBookingRequestsQuery.Data,
      Unit
  >(
    connector,
    GetBookingRequestsQuery.Companion.operationName,
    GetBookingRequestsQuery.Companion.dataDeserializer,
    GetBookingRequestsQuery.Companion.variablesSerializer,
  )


private class GetSpaceListingsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetSpaceListingsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetSpaceListingsQuery.Data,
      Unit
  >(
    connector,
    GetSpaceListingsQuery.Companion.operationName,
    GetSpaceListingsQuery.Companion.dataDeserializer,
    GetSpaceListingsQuery.Companion.variablesSerializer,
  )


private class GetSpaceSubdivisionsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetSpaceSubdivisionsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetSpaceSubdivisionsQuery.Data,
      Unit
  >(
    connector,
    GetSpaceSubdivisionsQuery.Companion.operationName,
    GetSpaceSubdivisionsQuery.Companion.dataDeserializer,
    GetSpaceSubdivisionsQuery.Companion.variablesSerializer,
  )


private class GetSpaceSubdivisionsBySpaceIdQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetSpaceSubdivisionsBySpaceIdQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetSpaceSubdivisionsBySpaceIdQuery.Data,
      GetSpaceSubdivisionsBySpaceIdQuery.Variables
  >(
    connector,
    GetSpaceSubdivisionsBySpaceIdQuery.Companion.operationName,
    GetSpaceSubdivisionsBySpaceIdQuery.Companion.dataDeserializer,
    GetSpaceSubdivisionsBySpaceIdQuery.Companion.variablesSerializer,
  )


private class GetSuccessfulTransactionsInRangeQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetSuccessfulTransactionsInRangeQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetSuccessfulTransactionsInRangeQuery.Data,
      GetSuccessfulTransactionsInRangeQuery.Variables
  >(
    connector,
    GetSuccessfulTransactionsInRangeQuery.Companion.operationName,
    GetSuccessfulTransactionsInRangeQuery.Companion.dataDeserializer,
    GetSuccessfulTransactionsInRangeQuery.Companion.variablesSerializer,
  )


private class GetWhishTransactionsQueryImpl(
  connector: ProspaceConnectorConnector
):
  GetWhishTransactionsQuery,
  ProspaceConnectorConnectorGeneratedQueryImpl<
      GetWhishTransactionsQuery.Data,
      Unit
  >(
    connector,
    GetWhishTransactionsQuery.Companion.operationName,
    GetWhishTransactionsQuery.Companion.dataDeserializer,
    GetWhishTransactionsQuery.Companion.variablesSerializer,
  )


private class InsertAuditSecurityLogMutationImpl(
  connector: ProspaceConnectorConnector
):
  InsertAuditSecurityLogMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      InsertAuditSecurityLogMutation.Data,
      InsertAuditSecurityLogMutation.Variables
  >(
    connector,
    InsertAuditSecurityLogMutation.Companion.operationName,
    InsertAuditSecurityLogMutation.Companion.dataDeserializer,
    InsertAuditSecurityLogMutation.Companion.variablesSerializer,
  )


private class InsertWhishTransactionMutationImpl(
  connector: ProspaceConnectorConnector
):
  InsertWhishTransactionMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      InsertWhishTransactionMutation.Data,
      InsertWhishTransactionMutation.Variables
  >(
    connector,
    InsertWhishTransactionMutation.Companion.operationName,
    InsertWhishTransactionMutation.Companion.dataDeserializer,
    InsertWhishTransactionMutation.Companion.variablesSerializer,
  )


private class UpsertAdminPricingStateMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertAdminPricingStateMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertAdminPricingStateMutation.Data,
      UpsertAdminPricingStateMutation.Variables
  >(
    connector,
    UpsertAdminPricingStateMutation.Companion.operationName,
    UpsertAdminPricingStateMutation.Companion.dataDeserializer,
    UpsertAdminPricingStateMutation.Companion.variablesSerializer,
  )


private class UpsertAppUserMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertAppUserMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertAppUserMutation.Data,
      UpsertAppUserMutation.Variables
  >(
    connector,
    UpsertAppUserMutation.Companion.operationName,
    UpsertAppUserMutation.Companion.dataDeserializer,
    UpsertAppUserMutation.Companion.variablesSerializer,
  )


private class UpsertAvatarCampaignMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertAvatarCampaignMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertAvatarCampaignMutation.Data,
      UpsertAvatarCampaignMutation.Variables
  >(
    connector,
    UpsertAvatarCampaignMutation.Companion.operationName,
    UpsertAvatarCampaignMutation.Companion.dataDeserializer,
    UpsertAvatarCampaignMutation.Companion.variablesSerializer,
  )


private class UpsertBookingRequestMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertBookingRequestMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertBookingRequestMutation.Data,
      UpsertBookingRequestMutation.Variables
  >(
    connector,
    UpsertBookingRequestMutation.Companion.operationName,
    UpsertBookingRequestMutation.Companion.dataDeserializer,
    UpsertBookingRequestMutation.Companion.variablesSerializer,
  )


private class UpsertSpaceListingMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertSpaceListingMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertSpaceListingMutation.Data,
      UpsertSpaceListingMutation.Variables
  >(
    connector,
    UpsertSpaceListingMutation.Companion.operationName,
    UpsertSpaceListingMutation.Companion.dataDeserializer,
    UpsertSpaceListingMutation.Companion.variablesSerializer,
  )


private class UpsertSpaceSubdivisionMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertSpaceSubdivisionMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertSpaceSubdivisionMutation.Data,
      UpsertSpaceSubdivisionMutation.Variables
  >(
    connector,
    UpsertSpaceSubdivisionMutation.Companion.operationName,
    UpsertSpaceSubdivisionMutation.Companion.dataDeserializer,
    UpsertSpaceSubdivisionMutation.Companion.variablesSerializer,
  )


private class UpsertWhishTransactionMutationImpl(
  connector: ProspaceConnectorConnector
):
  UpsertWhishTransactionMutation,
  ProspaceConnectorConnectorGeneratedMutationImpl<
      UpsertWhishTransactionMutation.Data,
      UpsertWhishTransactionMutation.Variables
  >(
    connector,
    UpsertWhishTransactionMutation.Companion.operationName,
    UpsertWhishTransactionMutation.Companion.dataDeserializer,
    UpsertWhishTransactionMutation.Companion.variablesSerializer,
  )


