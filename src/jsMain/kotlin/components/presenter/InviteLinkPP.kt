package components.presenter

import react.FC
import tools.confido.state.InviteLinkPV

val InviteLinkPP = FC<PresenterPageProps<InviteLinkPV>> { props->
    +"invite link ${props.view.id}"
}