import * as React from "react"

import "less/util.less"
import {MouseEvent} from "react";

interface PlanksProps {
    crushable: boolean
    imgNum: number
}

export default class Planks extends React.Component<PlanksProps, { crushed: boolean }> {
    constructor() {
        super();

        this.state = {
            crushed: false
        }
    }

    onClick = (e: MouseEvent<HTMLElement>) => {
        if (e.nativeEvent.detail == 3) {
            this.setState({
                crushed: true
            })
        }
    };

    render() {
        return this.state.crushed ? null : <div onClick={this.props.crushable ? this.onClick : undefined}
                                                className={`wooden-planks-${this.props.imgNum}`}/>
    }
}
